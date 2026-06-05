package com.mtole.taskmanager.auth;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
        @Schema(description= "JWT access token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String token
) {
}
