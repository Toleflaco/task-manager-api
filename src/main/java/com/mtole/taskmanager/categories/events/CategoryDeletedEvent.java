package com.mtole.taskmanager.categories.events;

import java.time.Instant;

public record CategoryDeletedEvent(
        Long categoryId,
        Long userId,
        String name,
        Instant occurredAt
) {}
