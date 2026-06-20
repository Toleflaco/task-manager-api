package com.mtole.taskmanager.categories.events;

import java.time.Instant;

public record CategoryCreatedEvent(
        Long categoryId,
        Long userId,
        String name,
        Instant occurredAt
) {}
