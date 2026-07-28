package com.clouddisk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record FolderCreateRequest(
        @NotBlank(message = "folderName must not be blank")
        @Size(max = 255, message = "folderName must not exceed 255 characters")
        String folderName,

        @NotNull(message = "parentId must not be null")
        @PositiveOrZero(message = "parentId must be greater than or equal to 0")
        Long parentId
) {
}
