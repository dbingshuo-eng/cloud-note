package com.clouddisk.dto;

import com.clouddisk.validation.ValidSharePassword;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record ShareCreateRequest(
        @NotNull(message = "fileId must not be null")
        @Positive(message = "fileId must be greater than 0")
        Long fileId,

        @ValidSharePassword
        String password,

        LocalDateTime expireTime
) {
}
