package com.clouddisk.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record FileMoveRequest(
        @NotNull(message = "parentId must not be null")
        @PositiveOrZero(message = "parentId must be greater than or equal to 0")
        Long parentId
) {
}
