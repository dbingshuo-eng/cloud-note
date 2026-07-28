package com.clouddisk.dto;

import com.clouddisk.validation.ValidSharePassword;
import jakarta.validation.constraints.NotNull;

public record ShareVerifyRequest(
        @NotNull(message = "password must not be null")
        @ValidSharePassword
        String password
) {
}
