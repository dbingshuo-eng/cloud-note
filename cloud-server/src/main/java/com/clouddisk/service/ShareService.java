package com.clouddisk.service;

import com.clouddisk.common.ApiException;
import com.clouddisk.common.AuthenticationException;
import com.clouddisk.dto.ShareCreateRequest;
import com.clouddisk.dto.ShareVerifyRequest;
import com.clouddisk.entity.FileRecord;
import com.clouddisk.entity.Share;
import com.clouddisk.mapper.FileRecordMapper;
import com.clouddisk.mapper.ShareMapper;
import com.clouddisk.utils.UserContext;
import com.clouddisk.validation.SharePasswordPolicy;
import com.clouddisk.vo.ShareAccessVO;
import com.clouddisk.vo.ShareCreateVO;
import com.clouddisk.vo.SharedFileVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ShareService {

    private static final int MAX_CODE_INSERT_ATTEMPTS = 5;

    private final ShareMapper shareMapper;
    private final FileRecordMapper fileRecordMapper;
    private final PasswordEncoder passwordEncoder;
    private final ShareCodeGenerator shareCodeGenerator;
    private final Clock clock;
    private final ShareVerificationRateLimiter verificationRateLimiter;

    public ShareService(ShareMapper shareMapper,
                        FileRecordMapper fileRecordMapper,
                        PasswordEncoder passwordEncoder,
                        ShareCodeGenerator shareCodeGenerator,
                        Clock clock,
                        ShareVerificationRateLimiter verificationRateLimiter) {
        this.shareMapper = shareMapper;
        this.fileRecordMapper = fileRecordMapper;
        this.passwordEncoder = passwordEncoder;
        this.shareCodeGenerator = shareCodeGenerator;
        this.clock = clock;
        this.verificationRateLimiter = verificationRateLimiter;
    }

    public ShareCreateVO create(ShareCreateRequest request) {
        Long userId = requireUserId();
        if (request == null || request.fileId() == null || request.fileId() <= 0) {
            throw new ApiException(400, "fileId must be greater than 0");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (request.expireTime() != null && !request.expireTime().isAfter(now)) {
            throw new ApiException(400, "expireTime must be in the future");
        }

        FileRecord file = fileRecordMapper.selectOwnedActiveById(request.fileId(), userId);
        if (file == null) {
            throw new ApiException(404, "File not found");
        }

        String password = StringUtils.hasText(request.password()) ? request.password() : null;
        validatePasswordLength(password);
        String passwordHash = password == null ? null : passwordEncoder.encode(password);

        for (int attempt = 0; attempt < MAX_CODE_INSERT_ATTEMPTS; attempt++) {
            String code = shareCodeGenerator.generate();
            Share share = new Share();
            share.setFileId(file.getId());
            share.setShareCode(code);
            share.setSharePassword(passwordHash);
            share.setExpireTime(request.expireTime());
            share.setCreateTime(now);
            try {
                if (shareMapper.insert(share) != 1) {
                    throw new ApiException(500, "Share persistence failed");
                }
                return new ShareCreateVO(code, share.getExpireTime(), password != null);
            } catch (DuplicateKeyException exception) {
                if (attempt == MAX_CODE_INSERT_ATTEMPTS - 1) {
                    throw new ApiException(503, "Unable to create share", exception);
                }
            }
        }

        throw new ApiException(503, "Unable to create share");
    }

    public ShareAccessVO get(String code) {
        Share share = requireShare(code);
        FileRecord file = requireActiveFile(share.getFileId());
        return toAccessVO(share, file);
    }

    public ShareAccessVO verify(String code, ShareVerifyRequest request) {
        Share share = requireShare(code);
        String passwordHash = share.getSharePassword();
        String password = request == null ? null : request.password();
        validatePasswordLength(password);
        if (StringUtils.hasText(passwordHash)) {
            if (!verificationRateLimiter.tryAcquire(share.getShareCode())) {
                throw new ApiException(429, "Too many share password attempts");
            }
            if (password == null || !passwordEncoder.matches(password, passwordHash)) {
                throw new ApiException(401, "Invalid share password");
            }
            verificationRateLimiter.clear(share.getShareCode());
        }
        FileRecord file = requireActiveFile(share.getFileId());
        return new ShareAccessVO(
                share.getShareCode(),
                StringUtils.hasText(passwordHash),
                share.getExpireTime(),
                toSharedFileVO(file)
        );
    }

    private Share requireShare(String code) {
        if (!StringUtils.hasText(code) || code.length() > 32) {
            throw new ApiException(404, "Share not found");
        }
        Share share = shareMapper.selectByShareCode(code);
        if (share == null) {
            throw new ApiException(404, "Share not found");
        }
        if (share.getExpireTime() != null
                && !share.getExpireTime().isAfter(LocalDateTime.now(clock))) {
            throw new ApiException(410, "Share expired");
        }
        return share;
    }

    private FileRecord requireActiveFile(Long fileId) {
        FileRecord file = fileRecordMapper.selectActiveById(fileId);
        if (file == null) {
            throw new ApiException(404, "Share not found");
        }
        return file;
    }

    private ShareAccessVO toAccessVO(Share share, FileRecord file) {
        boolean requiresPassword = StringUtils.hasText(share.getSharePassword());
        SharedFileVO sharedFile = requiresPassword ? null : toSharedFileVO(file);
        return new ShareAccessVO(
                share.getShareCode(),
                requiresPassword,
                share.getExpireTime(),
                sharedFile
        );
    }

    private SharedFileVO toSharedFileVO(FileRecord file) {
        return new SharedFileVO(
                file.getId(),
                file.getFileName(),
                file.getFileSize(),
                file.getFileType(),
                FileRecord.NODE_TYPE_FOLDER.equals(file.getNodeType()),
                file.getCreateTime(),
                file.getUpdateTime()
        );
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AuthenticationException("Missing or invalid Authorization header");
        }
        return userId;
    }

    private void validatePasswordLength(String password) {
        if (!SharePasswordPolicy.hasValidLength(password)) {
            throw new ApiException(400, SharePasswordPolicy.LENGTH_MESSAGE);
        }
    }
}
