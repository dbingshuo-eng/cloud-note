package com.clouddisk.dto;

import org.springframework.web.multipart.MultipartFile;

public record FileUploadRequest(MultipartFile file, Long parentId, String fileName) {
    public FileUploadRequest(MultipartFile file, Long parentId) {
        this(file, parentId, null);
    }
}
