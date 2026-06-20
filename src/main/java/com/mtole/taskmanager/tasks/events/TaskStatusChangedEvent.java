package com.mtole.taskmanager.tasks.events;

import java.time.Instant;

public record TaskStatusChangedEvent(
        Long taskId,
        Long userId,
        String oldStatus,
        String newStatus,
        Instant occurredAt
) {}
