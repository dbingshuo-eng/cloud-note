package com.clouddisk.vo;

import java.time.LocalDateTime;

public record ShareAccessVO(
        String code,
        boolean requiresPassword,
        LocalDateTime expireTime,
        SharedFileVO file
) {
}
