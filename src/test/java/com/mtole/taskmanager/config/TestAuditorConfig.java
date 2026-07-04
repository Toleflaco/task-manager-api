package com.mtole.taskmanager.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@TestConfiguration
public class TestAuditorConfig {

    public static final Long TEST_AUDITOR_ID = 1L;

    @Bean
    @Primary
    public AuditorAware<Long> securityAuditorAware() {
        return () -> Optional.of(TEST_AUDITOR_ID);
    }

    @Bean
    @Primary
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
