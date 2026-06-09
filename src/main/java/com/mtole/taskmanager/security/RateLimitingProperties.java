package com.mtole.taskmanager.security;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "rate-limit")
@Validated
public record RateLimitingProperties(
        @NotNull Login login
        ) {
    public record Login(
            @Positive int capacity,
            @NotNull Duration refillPeriod
    ){}
}
