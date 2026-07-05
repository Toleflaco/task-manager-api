package com.mtole.taskmanager.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@TestConfiguration
@EnableJpaAuditing(
        auditorAwareRef = "testAuditorAware",
        dateTimeProviderRef = "testDateTimeProvider"
)
public class TestAuditorConfig {

    public static final Long TEST_AUDITOR_ID = 1L;

    @Bean
    public AuditorAware<Long> testAuditorAware() {
        return () -> Optional.of(TEST_AUDITOR_ID);
    }

    @Bean
    public DateTimeProvider testDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
