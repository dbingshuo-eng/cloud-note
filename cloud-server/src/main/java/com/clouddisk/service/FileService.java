package com.clouddisk.service;

import com.clouddisk.common.ApiException;
import com.clouddisk.common.AuthenticationException;
import com.clouddisk.dto.FileUploadRequest;
import com.clouddisk.dto.FolderCreateRequest;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.mapper.FileRecordMapper;
import com.clouddisk.utils.UserContext;
import com.clouddisk.vo.FileDownloadVO;
import com.clouddisk.vo.FileInfoVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class FileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileService.class);
    private static final long MAX_FILE_SIZE_BYTES = 100_000_000L;
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final int MAX_FOLDER_DEPTH = 32;

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "fileName", "file_name",
            "fileSize", "file_size",
            "fileType", "file_type",
            "createTime", "create_time",
            "updateTime", "update_time"
    );

    private final ObjectStorageService objectStorageService;
    private final FileRecordMapper fileRecordMapper;
    private final FileListCacheService fileListCacheService;

    public FileService(
            ObjectStorageService objectStorageService,
            FileRecordMapper fileRecordMapper,
            FileListCacheService fileListCacheService
    ) {
        this.objectStorageService = objectStorageService;
        this.fileRecordMapper = fileRecordMapper;
        this.fileListCacheService = fileListCacheService;
    }

    @Transactional
    public FileInfoVO upload(FileUploadRequest request) {
        Long userId = requireUserId();

        if (request == null || request.file() == null || request.file().isEmpty()) {
            throw new ApiException(400, "file must not be empty");
        }
        if (request.file().getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ApiException(413, "File exceeds maximum size of 100000000 bytes");
        }

        Long parentId = request.parentId() == null ? 0L : request.parentId();
        if (parentId < 0) {
            throw new ApiException(400, "parentId must be greater than or equal to 0");
        }

        String fileName = StringUtils.hasText(request.fileName())
                ? normalizeRenameName(request.fileName())
                : extractSafeFileName(request.file());
        lockActiveParentPath(userId, parentId);

        if (fileRecordMapper.selectActiveDuplicate(userId, parentId, fileName) != null) {
            throw new ApiException(409, "File already exists in target folder");
        }

        String objectKey = generateObjectKey(userId, fileName);
        UploadMetadata uploadMetadata = uploadObject(request.file(), objectKey);
        AtomicBoolean rollbackCleanupDone = registerUploadedObjectRollbackCleanup(objectKey);
        FileRecord fileRecord = toFileRecord(userId, parentId, fileName, objectKey, uploadMetadata);
        try {
            int insertedRows = fileRecordMapper.insert(fileRecord);
            if (insertedRows != 1) {
                throw new ApiException(500, "File metadata persistence failed");
            }
        } catch (DuplicateKeyException exception) {
            ApiException conflict = new ApiException(
                    409,
                    "File already exists in target folder",
                    exception
            );
            rollbackUploadedObject(objectKey, conflict, rollbackCleanupDone);
            throw conflict;
        } catch (RuntimeException exception) {
            rollbackUploadedObject(objectKey, exception, rollbackCleanupDone);
            throw exception;
        }

        invalidateAfterMutation(userId, parentId, null);
        return toFileInfo(fileRecord);
    }

    public List<FileInfoVO> list(Long parentId, String sort, String order) {
        Long userId = requireUserId();
        Long normalizedParentId = normalizeParentId(parentId);
        String sortColumn = SORT_COLUMNS.get(sort);
        if (sortColumn == null) {
            throw new ApiException(400, "Unsupported sort field");
        }
        String sortOrder = normalizeSortOrder(order);

        validateParent(userId, normalizedParentId);
        FileListCacheService.CacheRead cacheRead = fileListCacheService.getWithGeneration(
                userId,
                normalizedParentId,
                sortColumn,
                sortOrder
        );
        if (cacheRead.files().isPresent()) {
            return cacheRead.files().orElseThrow();
        }

        List<FileInfoVO> files = fileRecordMapper.selectOwnedActiveChildren(
                        userId,
                        normalizedParentId,
                        sortColumn,
                        sortOrder
                )
                .stream()
                .map(this::toFileInfo)
                .toList();
        fileListCacheService.putIfGenerationUnchanged(
                userId,
                normalizedParentId,
                sortColumn,
                sortOrder,
                cacheRead.generation(),
                files
        );
        return files;
    }

    public List<FileInfoVO> search(String keyword) {
        Long userId = requireUserId();
        if (!StringUtils.hasText(keyword)) {
            throw new ApiException(400, "keyword must not be blank");
        }
        String normalizedKeyword = keyword.trim();
        if (normalizedKeyword.length() > 100) {
            throw new ApiException(400, "keyword must not exceed 100 characters");
        }
        return fileRecordMapper.selectOwnedActiveSearch(userId, normalizedKeyword)
                .stream()
                .map(this::toFileInfo)
                .toList();
    }

    @Transactional
    public void move(Long id, Long targetParentId) {
        Long userId = requireUserId();
        validateId(id);
        Long normalizedTargetParentId = normalizeParentId(targetParentId);
        FileRecord source = fileRecordMapper.selectOwnedActiveByIdForUpdate(id, userId);
        if (source == null) {
            throw new ApiException(404, "File not found");
        }
        validateParent(userId, normalizedTargetParentId);
        if (isFolder(source)) {
            List<Long> subtreeIds = fileRecordMapper.selectOwnedActiveIdsInSubtree(id, userId);
            if (subtreeIds != null && subtreeIds.contains(normalizedTargetParentId)) {
                throw new ApiException(400, "Cannot move a folder into itself or its descendants");
            }
        }
        Long oldParentId = normalizeParentId(source.getParentId());
        if (oldParentId.equals(normalizedTargetParentId)) {
            return;
        }
        if (fileRecordMapper.selectActiveDuplicate(userId, normalizedTargetParentId, source.getFileName()) != null) {
            throw duplicateNameConflict();
        }
        if (fileRecordMapper.updateOwnedActiveParent(id, userId, normalizedTargetParentId) != 1) {
            throw new ApiException(404, "File not found");
        }
        List<Long> folderIds = ownedFolderIdsInSubtree(source, userId);
        invalidateAfterMutation(userId, oldParentId, folderIds);
        invalidateAfterMutation(userId, normalizedTargetParentId, folderIds);
    }

    @Transactional
    public FileInfoVO createFolder(FolderCreateRequest request) {
        Long userId = requireUserId();
        if (request == null || !StringUtils.hasText(request.folderName())) {
            throw new ApiException(400, "folderName must not be blank");
        }

        String folderName = request.folderName().trim();
        if (folderName.length() > 255) {
            throw new ApiException(400, "folderName must not exceed 255 characters");
        }
        Long parentId = normalizeParentId(request.parentId());
        int parentDepth = lockActiveParentPath(userId, parentId);
        if (parentDepth >= MAX_FOLDER_DEPTH) {
            throw new ApiException(400, "Folder depth must not exceed 32");
        }
        if (fileRecordMapper.selectActiveDuplicate(userId, parentId, folderName) != null) {
            throw duplicateNameConflict();
        }

        LocalDateTime now = LocalDateTime.now();
        FileRecord folder = new FileRecord();
        folder.setUserId(userId);
        folder.setParentId(parentId);
        folder.setFileName(folderName);
        folder.setFileSize(0L);
        folder.setFileType("folder");
        folder.setNodeType(FileRecord.NODE_TYPE_FOLDER);
        folder.setFileUrl(null);
        folder.setFilePath(null);
        folder.setMd5(null);
        folder.setIsDelete(0);
        folder.setCreateTime(now);
        folder.setUpdateTime(now);
        try {
            if (fileRecordMapper.insert(folder) != 1) {
                throw new ApiException(500, "Folder metadata persistence failed");
            }
        } catch (DuplicateKeyException exception) {
            throw duplicateNameConflict(exception);
        }
        invalidateAfterMutation(userId, parentId, null);
        return toFileInfo(folder);
    }

    @Transactional
    public void rename(Long id, String fileName) {
        Long userId = requireUserId();
        validateId(id);
        String normalizedName = normalizeRenameName(fileName);
        FileRecord active = fileRecordMapper.selectOwnedActiveById(id, userId);
        if (active == null) {
            throw new ApiException(404, "File not found");
        }

        Long parentId = normalizeParentId(active.getParentId());
        FileRecord duplicate = fileRecordMapper.selectActiveDuplicate(userId, parentId, normalizedName);
        if (duplicate != null && !id.equals(duplicate.getId())) {
            throw duplicateNameConflict();
        }

        try {
            if (fileRecordMapper.updateOwnedActiveName(id, userId, normalizedName) != 1) {
                throw new ApiException(404, "File not found");
            }
        } catch (DuplicateKeyException exception) {
            throw duplicateNameConflict(exception);
        }
        invalidateAfterMutation(userId, parentId, null);
    }

    @Transactional
    public void delete(Long id) {
        Long userId = requireUserId();
        validateId(id);
        FileRecord active = fileRecordMapper.selectOwnedActiveById(id, userId);
        if (active == null) {
            throw new ApiException(404, "File not found");
        }
        List<Long> folderIds = ownedFolderIdsInSubtree(active, userId);
        List<Long> subtreeIds = fileRecordMapper.selectOwnedActiveIdsInSubtree(id, userId);
        if (subtreeIds == null || subtreeIds.isEmpty()
                || fileRecordMapper.softDeleteOwnedActiveByIds(subtreeIds, userId) != subtreeIds.size()) {
            throw new ApiException(404, "File not found");
        }
        invalidateAfterMutation(
                userId,
                normalizeParentId(active.getParentId()),
                folderIds
        );
    }

    @Transactional
    public void delete(List<Long> ids) {
        Long userId = requireUserId();
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(400, "No files selected");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(ids);
        if (uniqueIds.size() > 100) {
            throw new ApiException(400, "Too many files selected");
        }

        Set<Long> allIds = new LinkedHashSet<>();
        Set<Long> parentIds = new LinkedHashSet<>();
        Set<Long> folderIds = new LinkedHashSet<>();
        for (Long id : uniqueIds) {
            validateId(id);
            FileRecord active = fileRecordMapper.selectOwnedActiveById(id, userId);
            if (active == null) {
                throw new ApiException(404, "File not found");
            }
            parentIds.add(normalizeParentId(active.getParentId()));
            folderIds.addAll(ownedFolderIdsInSubtree(active, userId));
            List<Long> subtreeIds = fileRecordMapper.selectOwnedActiveIdsInSubtree(id, userId);
            if (subtreeIds == null || subtreeIds.isEmpty()) {
                throw new ApiException(404, "File not found");
            }
            allIds.addAll(subtreeIds);
        }

        List<Long> allIdsList = List.copyOf(allIds);
        if (fileRecordMapper.softDeleteOwnedActiveByIds(allIdsList, userId) != allIdsList.size()) {
            throw new ApiException(404, "File not found");
        }
        for (Long parentId : parentIds) {
            invalidateAfterMutation(userId, parentId, List.copyOf(folderIds));
        }
    }

    public List<FileInfoVO> recycle() {
        Long userId = requireUserId();
        return fileRecordMapper.selectOwnedDeleted(userId)
                .stream()
                .map(this::toFileInfo)
                .toList();
    }

    @Transactional
    public void permanentlyDelete(Long id) {
        validateId(id);
        permanentlyDelete(List.of(id));
    }

    @Transactional
    public void permanentlyDelete(List<Long> ids) {
        Long userId = requireUserId();
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(400, "No files selected");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(ids);
        if (uniqueIds.size() > 100) {
            throw new ApiException(400, "Too many files selected");
        }

        List<FileRecord> records = new ArrayList<>();
        Set<Long> recordIds = new LinkedHashSet<>();
        Set<Long> parentIds = new LinkedHashSet<>();
        Set<Long> folderIds = new LinkedHashSet<>();
        for (Long id : uniqueIds) {
            validateId(id);
            FileRecord deleted = fileRecordMapper.selectOwnedDeletedById(id, userId);
            if (deleted == null) {
                throw new ApiException(404, "File not found");
            }
            parentIds.add(normalizeParentId(deleted.getParentId()));
            folderIds.addAll(ownedFolderIdsInSubtree(deleted, userId));

            List<FileRecord> subtree = fileRecordMapper.selectOwnedDeletedSubtree(id, userId);
            if (subtree == null || subtree.isEmpty()) {
                throw new ApiException(404, "File not found");
            }
            for (FileRecord record : subtree) {
                if (recordIds.add(record.getId())) {
                    records.add(record);
                }
            }
        }

        for (FileRecord record : records) {
            if (isFolder(record) || !StringUtils.hasText(record.getFilePath())) {
                continue;
            }
            try {
                objectStorageService.delete(record.getFilePath());
            } catch (ObjectStorageException exception) {
                throw new ApiException(502, "Object storage unavailable", exception);
            }
        }

        List<Long> deletedIds = List.copyOf(recordIds);
        if (fileRecordMapper.deleteOwnedDeletedByIds(deletedIds, userId) != deletedIds.size()) {
            throw new ApiException(404, "File not found");
        }

        for (Long parentId : parentIds) {
            invalidateAfterMutation(userId, parentId, List.copyOf(folderIds));
        }
    }

    @Transactional
    public void recover(Long id) {
        Long userId = requireUserId();
        validateId(id);
        FileRecord deleted = fileRecordMapper.selectOwnedDeletedById(id, userId);
        if (deleted == null) {
            throw new ApiException(404, "File not found");
        }

        Long parentId = normalizeParentId(deleted.getParentId());
        lockRecoveryParent(userId, parentId);
        if (fileRecordMapper.selectActiveDuplicate(userId, parentId, deleted.getFileName()) != null) {
            throw duplicateNameConflict();
        }
        List<Long> folderIds = ownedFolderIdsInSubtree(deleted, userId);

        try {
            if (fileRecordMapper.recoverOwnedDeleted(id, userId) < 1) {
                throw new ApiException(404, "File not found");
            }
        } catch (DuplicateKeyException exception) {
            throw duplicateNameConflict(exception);
        }
        invalidateAfterMutation(
                userId,
                parentId,
                folderIds
        );
    }

    private void lockRecoveryParent(Long userId, Long parentId) {
        if (parentId == 0L) {
            return;
        }

        if (fileRecordMapper.selectOwnedActiveFolderByIdForUpdate(parentId, userId) == null) {
            throw new ApiException(404, "Parent folder not found");
        }
    }

    public FileDownloadVO download(Long id) {
        Long userId = requireUserId();
        validateId(id);
        FileRecord fileRecord = fileRecordMapper.selectOwnedActiveById(id, userId);
        if (fileRecord == null) {
            throw new ApiException(404, "File not found");
        }
        if (isFolder(fileRecord)) {
            throw new ApiException(400, "Folders cannot be downloaded");
        }
        if (!StringUtils.hasText(fileRecord.getFilePath())) {
            throw new ApiException(404, "File not found");
        }

        String objectKey = fileRecord.getFilePath();
        preflightDownload(objectKey);
        long contentLength = fileRecord.getFileSize() == null ? 0L : fileRecord.getFileSize();
        return new FileDownloadVO(
                fileRecord.getFileName(),
                contentLength,
                () -> openDownloadStream(objectKey)
        );
    }

    private void preflightDownload(String objectKey) {
        try {
            objectStorageService.stat(objectKey);
        } catch (ObjectStorageException exception) {
            throw new ApiException(502, "Object storage unavailable", exception);
        }
    }

    private InputStream openDownloadStream(String objectKey) {
        try {
            InputStream inputStream = objectStorageService.download(objectKey);
            if (inputStream == null) {
                throw new ObjectStorageException("Object storage unavailable");
            }
            return inputStream;
        } catch (ObjectStorageException exception) {
            throw new ApiException(502, "Object storage unavailable", exception);
        }
    }

    private UploadMetadata uploadObject(MultipartFile file, String objectKey) {
        MessageDigest digest = newMd5Digest();
        boolean uploaded = false;
        try (InputStream inputStream = file.getInputStream();
             DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
            objectStorageService.upload(
                    objectKey,
                    digestInputStream,
                    file.getSize(),
                    file.getContentType()
            );
            uploaded = true;
            return new UploadMetadata(file.getSize(), HexFormat.of().formatHex(digest.digest()));
        } catch (ObjectStorageException exception) {
            throw new ApiException(502, "Object storage unavailable", exception);
        } catch (IOException exception) {
            ApiException apiException = new ApiException(400, "Unable to read upload", exception);
            if (uploaded) {
                rollbackUploadedObject(objectKey, apiException);
            }
            throw apiException;
        }
    }

    private void validateParent(Long userId, Long parentId) {
        if (parentId == 0L) {
            return;
        }

        FileRecord parent = fileRecordMapper.selectOwnedActiveFolderById(parentId, userId);
        if (parent == null || !isFolder(parent)) {
            throw new ApiException(404, "Parent folder not found");
        }
    }

    private int lockActiveParentPath(Long userId, Long parentId) {
        if (parentId == 0L) {
            return 0;
        }

        Set<Long> visited = new HashSet<>();
        Long currentId = parentId;
        int depth = 0;
        while (currentId != 0L) {
            if (!visited.add(currentId) || depth >= MAX_FOLDER_DEPTH) {
                throw new ApiException(400, "Folder depth must not exceed 32");
            }

            FileRecord folder = fileRecordMapper.selectOwnedActiveFolderByIdForUpdate(currentId, userId);
            if (folder == null || !isFolder(folder)) {
                throw new ApiException(404, "Parent folder not found");
            }

            depth++;
            Long nextParentId = folder.getParentId();
            if (nextParentId == null || nextParentId < 0) {
                throw new ApiException(400, "Invalid folder hierarchy");
            }
            currentId = nextParentId;
        }
        return depth;
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AuthenticationException("Missing or invalid Authorization header");
        }
        return userId;
    }

    private Long normalizeParentId(Long parentId) {
        if (parentId == null) {
            throw new ApiException(400, "parentId must not be null");
        }
        if (parentId < 0) {
            throw new ApiException(400, "parentId must be greater than or equal to 0");
        }
        return parentId;
    }

    private String normalizeSortOrder(String order) {
        if ("asc".equalsIgnoreCase(order)) {
            return "ASC";
        }
        if ("desc".equalsIgnoreCase(order)) {
            return "DESC";
        }
        throw new ApiException(400, "Unsupported sort order");
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new ApiException(400, "id must be greater than 0");
        }
    }

    private ApiException duplicateNameConflict() {
        return new ApiException(409, "File already exists in target folder");
    }

    private ApiException duplicateNameConflict(DuplicateKeyException cause) {
        return new ApiException(409, "File already exists in target folder", cause);
    }

    private String extractSafeFileName(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFileName)) {
            throw new ApiException(400, "file name must not be blank");
        }

        String cleaned = StringUtils.cleanPath(originalFileName).replace('\\', '/');
        return normalizeRenameName(cleaned.substring(cleaned.lastIndexOf('/') + 1));
    }

    private String normalizeRenameName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new ApiException(400, "file name must not be blank");
        }
        String normalized = fileName.trim();
        if (normalized.length() > MAX_FILE_NAME_LENGTH) {
            throw new ApiException(400, "file name must not exceed 255 characters");
        }
        if (".".equals(normalized)
                || "..".equals(normalized)
                || normalized.indexOf('/') >= 0
                || normalized.indexOf('\\') >= 0
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new ApiException(400, "file name contains invalid characters");
        }
        return normalized;
    }

    private MessageDigest newMd5Digest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 algorithm not available", exception);
        }
    }

    private String generateObjectKey(Long userId, String fileName) {
        String extension = extractExtension(fileName);
        LocalDate today = LocalDate.now();

        return "users/%d/%04d/%02d/%02d/%s%s".formatted(
                userId,
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                UUID.randomUUID(),
                extension
        );
    }

    private String extractExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
            return "";
        }

        String normalized = fileName.substring(extensionIndex + 1)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        if (!StringUtils.hasText(normalized)) {
            return "";
        }

        return "." + normalized;
    }

    private FileRecord toFileRecord(Long userId, Long parentId, String fileName, String objectKey, UploadMetadata uploadMetadata) {
        LocalDateTime now = LocalDateTime.now();
        FileRecord fileRecord = new FileRecord();
        fileRecord.setUserId(userId);
        fileRecord.setParentId(parentId);
        fileRecord.setFileName(fileName);
        fileRecord.setFileSize(uploadMetadata.size());
        fileRecord.setFileType(fileType(fileName));
        fileRecord.setNodeType(FileRecord.NODE_TYPE_FILE);
        fileRecord.setFileUrl(null);
        fileRecord.setFilePath(objectKey);
        fileRecord.setMd5(uploadMetadata.md5());
        fileRecord.setIsDelete(0);
        fileRecord.setCreateTime(now);
        fileRecord.setUpdateTime(now);
        return fileRecord;
    }

    private String fileType(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void rollbackUploadedObject(String objectKey, RuntimeException originalException) {
        rollbackUploadedObject(objectKey, originalException, new AtomicBoolean(false));
    }

    private void rollbackUploadedObject(
            String objectKey,
            RuntimeException originalException,
            AtomicBoolean cleanupDone
    ) {
        if (!cleanupDone.compareAndSet(false, true)) {
            return;
        }
        try {
            objectStorageService.delete(objectKey);
        } catch (RuntimeException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }

    private AtomicBoolean registerUploadedObjectRollbackCleanup(String objectKey) {
        AtomicBoolean cleanupDone = new AtomicBoolean(false);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return cleanupDone;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    cleanupDone.set(true);
                    return;
                }
                if (!cleanupDone.compareAndSet(false, true)) {
                    return;
                }
                try {
                    objectStorageService.delete(objectKey);
                } catch (RuntimeException cleanupException) {
                    LOGGER.warn(
                            "upload_cleanup event=transaction_rollback_failed objectKey={} exception={}",
                            objectKey,
                            cleanupException.getClass().getSimpleName()
                    );
                }
            }
        });
        return cleanupDone;
    }

    private void invalidateAfterMutation(Long userId, Long parentId, List<Long> folderIds) {
        Set<Long> listParentIds = new LinkedHashSet<>();
        listParentIds.add(parentId);
        if (folderIds != null) {
            listParentIds.addAll(folderIds);
        }

        Runnable invalidation = () -> {
            fileListCacheService.advanceGeneration(userId);
            listParentIds.forEach(folderId ->
                    fileListCacheService.invalidateList(userId, folderId)
            );
            fileListCacheService.invalidateRecycleBin(userId);
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidation.run();
                }
            });
            return;
        }
        invalidation.run();
    }

    private List<Long> ownedFolderIdsInSubtree(FileRecord root, Long userId) {
        if (!isFolder(root)) {
            return List.of();
        }

        Set<Long> folderIds = new LinkedHashSet<>();
        folderIds.add(root.getId());
        List<Long> subtreeFolderIds = fileRecordMapper.selectOwnedFolderIdsInSubtree(
                root.getId(),
                userId,
                root.getIsDelete()
        );
        if (subtreeFolderIds != null) {
            folderIds.addAll(subtreeFolderIds);
        }
        return List.copyOf(folderIds);
    }

    private boolean isFolder(FileRecord fileRecord) {
        return FileRecord.NODE_TYPE_FOLDER.equals(fileRecord.getNodeType());
    }

    private FileInfoVO toFileInfo(FileRecord fileRecord) {
        return new FileInfoVO(
                fileRecord.getId(),
                fileRecord.getParentId(),
                fileRecord.getFileName(),
                fileRecord.getFileSize(),
                fileRecord.getFileType(),
                isFolder(fileRecord),
                fileRecord.getMd5(),
                fileRecord.getCreateTime(),
                fileRecord.getUpdateTime()
        );
    }

    private record UploadMetadata(long size, String md5) {
    }
}
