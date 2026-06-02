package com.mtole.taskmanager.tasks;

public record TaskFilter(
        TaskStatus status,
        Priority priority,
        Long categoryId
) {
}
