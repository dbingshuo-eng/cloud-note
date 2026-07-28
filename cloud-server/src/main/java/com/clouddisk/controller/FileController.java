package com.clouddisk.controller;

import com.clouddisk.common.ApiResponse;
import com.clouddisk.dto.FileBatchDeleteRequest;
import com.clouddisk.dto.FileMoveRequest;
import com.clouddisk.dto.FileRenameRequest;
import com.clouddisk.dto.FileUploadRequest;
import com.clouddisk.dto.FolderCreateRequest;
import com.clouddisk.service.FileService;
import com.clouddisk.vo.FileDownloadVO;
import com.clouddisk.vo.FileInfoVO;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/file")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileInfoVO> upload(@RequestPart("file") MultipartFile file,
                                          @RequestParam(name = "parentId", required = false) Long parentId,
                                          @RequestParam(name = "fileName", required = false) String fileName) {
        return ApiResponse.success(fileService.upload(new FileUploadRequest(file, parentId, fileName)));
    }

    @GetMapping("/list")
    public ApiResponse<List<FileInfoVO>> list(
            @RequestParam(name = "parentId", defaultValue = "0") Long parentId,
            @RequestParam(name = "sort", defaultValue = "createTime") String sort,
            @RequestParam(name = "order", defaultValue = "desc") String order) {
        return ApiResponse.success(fileService.list(parentId, sort, order));
    }

    @GetMapping("/search")
    public ApiResponse<List<FileInfoVO>> search(@RequestParam("keyword") String keyword) {
        return ApiResponse.success(fileService.search(keyword));
    }

    @PostMapping("/folder")
    public ApiResponse<FileInfoVO> createFolder(@Valid @RequestBody FolderCreateRequest request) {
        return ApiResponse.success(fileService.createFolder(request));
    }

    @PutMapping("/{id}/name")
    public ApiResponse<Void> rename(@PathVariable("id") Long id,
                                    @RequestBody FileRenameRequest request) {
        fileService.rename(id, request == null ? null : request.fileName());
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/parent")
    public ApiResponse<Void> move(@PathVariable("id") Long id,
                                  @Valid @RequestBody FileMoveRequest request) {
        fileService.move(id, request.parentId());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        fileService.delete(id);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/batch")
    public ApiResponse<Void> deleteBatch(@RequestBody FileBatchDeleteRequest request) {
        fileService.delete(request == null ? null : request.ids());
        return ApiResponse.success(null);
    }

    @GetMapping("/recycle")
    public ApiResponse<List<FileInfoVO>> recycle() {
        return ApiResponse.success(fileService.recycle());
    }

    @DeleteMapping("/recycle/{id}")
    public ApiResponse<Void> permanentlyDelete(@PathVariable("id") Long id) {
        fileService.permanentlyDelete(id);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/recycle/batch")
    public ApiResponse<Void> permanentlyDeleteBatch(@RequestBody FileBatchDeleteRequest request) {
        fileService.permanentlyDelete(request == null ? null : request.ids());
        return ApiResponse.success(null);
    }

    @PutMapping("/recover/{id}")
    public ApiResponse<Void> recover(@PathVariable("id") Long id) {
        fileService.recover(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable("id") Long id) {
        FileDownloadVO download = fileService.download(id);
        String fileName = safeDownloadName(download.fileName());
        MediaType contentType = MediaTypeFactory.getMediaType(fileName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        StreamingResponseBody responseBody = outputStream -> {
            try (var inputStream = download.inputStream()) {
                inputStream.transferTo(outputStream);
            }
        };

        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(download.contentLength())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileName, StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .header("X-Content-Type-Options", "nosniff")
                .body(responseBody);
    }

    private String safeDownloadName(String fileName) {
        if (fileName == null) {
            return "download";
        }
        String safeName = fileName
                .replace('\\', '_')
                .replace('/', '_')
                .replaceAll("\\p{Cntrl}", "_")
                .trim();
        return safeName.isEmpty() ? "download" : safeName;
    }
}
