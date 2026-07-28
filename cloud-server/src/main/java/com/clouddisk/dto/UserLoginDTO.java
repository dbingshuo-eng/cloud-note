package com.clouddisk.dto;

import jakarta.validation.constraints.NotBlank;

public record UserLoginDTO(@NotBlank(message = "code must not be blank") String code) {
}
