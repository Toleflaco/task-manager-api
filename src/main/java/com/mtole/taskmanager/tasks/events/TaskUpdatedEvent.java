package com.mtole.taskmanager.tasks.events;

import java.time.Instant;

public record TaskUpdatedEvent(
        Long taskId,
        Long userId,
        Instant occurredAt
) {}
