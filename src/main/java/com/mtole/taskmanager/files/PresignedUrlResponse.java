package com.mtole.taskmanager.files;

import java.time.Instant;

public record PresignedUrlResponse(
        String key,
        String url,
        Instant expiresAt
) {
}
