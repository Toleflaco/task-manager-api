package com.mtole.taskmanager.activity;

import java.time.Instant;

public record ActivityEventFilter(
        Instant from,
        Instant to,
        String resourceType,
        Long resourceId

) {
}
