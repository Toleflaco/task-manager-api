package com.mtole.taskmanager.categories.events;

import java.time.Instant;

public record CategoryUpdatedEvent(
        Long categoryId,
        Long userId,
        Instant occurredAt
) {}
