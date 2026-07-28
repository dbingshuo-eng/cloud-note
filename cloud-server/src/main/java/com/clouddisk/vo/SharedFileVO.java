package com.clouddisk.vo;

import java.time.LocalDateTime;

public record SharedFileVO(
        Long id,
        String fileName,
        Long fileSize,
        String fileType,
        boolean isFolder,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
    public SharedFileVO(
            Long id,
            String fileName,
            Long fileSize,
            String fileType,
            LocalDateTime createTime,
            LocalDateTime updateTime
    ) {
        this(id, fileName, fileSize, fileType, false, createTime, updateTime);
    }
}
