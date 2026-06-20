package com.mtole.taskmanager.activity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Smoke test temporal: inserta un evento de prueba al arrancar la app.
 *
 * Solo activo en perfil 'dev'. Se eliminará cuando el módulo activity
 * tenga emisión de eventos real desde los services en sesiones
 * siguientes.
 */
@Component
@Profile("dev")
public class ActivitySmokeTest implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ActivitySmokeTest.class);

    private final ActivityEventRepository repository;

    public ActivitySmokeTest(ActivityEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        log.info("=== ActivitySmokeTest: inserting test event ===");

        ActivityEvent event = new ActivityEvent(
                1L,                                                       // userId
                "TASK_CREATED",                                           // action
                "TASK",                                                   // resourceType
                42L,                                                      // resourceId
                Map.of(),                                                 // before (vacío en CREATE)
                Map.of("title", "Comprar pan", "status", "PENDING"),      // after
                Instant.now()                                             // timestamp
        );

        ActivityEvent saved = repository.save(event);

        log.info("=== Event saved with id: {} ===", saved.getId());
        log.info("=== Total events in collection: {} ===", repository.count());
    }
}