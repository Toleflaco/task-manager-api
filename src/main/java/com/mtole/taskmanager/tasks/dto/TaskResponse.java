package com.mtole.taskmanager.tasks.dto;

import com.mtole.taskmanager.tasks.Priority;
import com.mtole.taskmanager.tasks.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record TaskResponse(
        @Schema(description = "Task id", example = "32")
        Long id,
        @Schema(description = "Task title", example = "Work in roadmap Java")
        String title,
        @Schema(description = "Task description", example = "Working on the Java roadmap to become a professional backend developer with Claude")
        String description,
        @Schema(description = "Task status", example = "PENDING")
        TaskStatus status,
        @Schema(description = "Task priority", example = "LOW")
        Priority priority,
        @Schema(description = "Date created task", example = "2026-06-02T16:02:30")
        LocalDateTime createdAt,
        @Schema(description = "Task dueDate", example = "2026-06-02T16:02:30")
        LocalDateTime dueDate,
        @Schema(description = "Completion timestamp, null if task is not completed yet", example = "2026-06-02T16:02:30")
        LocalDateTime completedAt,
        @Schema(description = "Category Id", example = "12")
        Long categoryId
) {
}
