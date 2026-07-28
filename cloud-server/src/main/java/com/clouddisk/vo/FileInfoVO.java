package com.clouddisk.vo;

import java.time.LocalDateTime;

public record FileInfoVO(
        Long id,
        Long parentId,
        String fileName,
        Long fileSize,
        String fileType,
        boolean isFolder,
        String md5,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
    public FileInfoVO(
            Long id,
            Long parentId,
            String fileName,
            Long fileSize,
            String fileType,
            String md5,
            LocalDateTime createTime,
            LocalDateTime updateTime
    ) {
        this(id, parentId, fileName, fileSize, fileType, false, md5, createTime, updateTime);
    }
}
