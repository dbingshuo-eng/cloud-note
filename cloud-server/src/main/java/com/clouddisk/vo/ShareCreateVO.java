package com.clouddisk.vo;

import java.time.LocalDateTime;

public record ShareCreateVO(
        String code,
        LocalDateTime expireTime,
        boolean requiresPassword
) {
}
