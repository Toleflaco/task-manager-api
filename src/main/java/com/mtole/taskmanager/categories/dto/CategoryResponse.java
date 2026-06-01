package com.mtole.taskmanager.categories.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record CategoryResponse(
        @Schema(description = "Category id", example = "32")
        Long id,
        @Schema(description = "Category name", example = "Work")
        String name,
        @Schema(description = "Category description", example = "Work-related category")
        String description,
        @Schema(description = "Category creation date", example = "2026-01-15T10:30:00")
        LocalDateTime createdAt
        ) {
}
