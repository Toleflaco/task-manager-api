package com.mtole.taskmanager.tasks;

import com.mtole.taskmanager.config.TestAuditorConfig;
import com.mtole.taskmanager.users.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.assertj.core.api.SoftAssertions;


import java.util.Optional;

import static com.mtole.taskmanager.config.TestAuditorConfig.TEST_AUDITOR_ID;
import static com.mtole.taskmanager.tasks.TaskTestDataBuilder.aTask;
import static com.mtole.taskmanager.users.UserTestDataBuilder.aUser;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestAuditorConfig.class)
class TaskRepositoryIT {


    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:14");

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    TestEntityManager entityManager;


    @Test
    @DisplayName("context loads and container starts")
    void contextLoads() {

        // Arrange

        // Act

        // Asserts
        assertThat(taskRepository).isNotNull();
    }

    @Test
    @DisplayName("persists a task with auditing fields")
    void saveTask_persistsTaskWithAuditingFieldsPopulated() {

        // Arrange
        String expectedTitle = "any title";
        User user = aUser().build();
        entityManager.persistAndFlush(user);
        Task task = aTask().withUser(user).withTitle(expectedTitle).build();

        // Act
        Task savedTask = taskRepository.saveAndFlush(task);
        entityManager.clear();
        Optional<Task> foundTask = taskRepository.findById(savedTask.getId());

        // Asserts
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(foundTask).isPresent();
            softly.assertThat(foundTask.get().getId()).isNotNull();
            softly.assertThat(foundTask.get().getTitle()).isEqualTo(expectedTitle);
            softly.assertThat(foundTask.get().getStatus()).isEqualTo(savedTask.getStatus());
            softly.assertThat(foundTask.get().getPriority()).isEqualTo(savedTask.getPriority());
            softly.assertThat(foundTask.get().getCreatedAt()).isNotNull();
            softly.assertThat(foundTask.get().getUpdatedAt()).isNotNull();
            softly.assertThat(foundTask.get().getCreatedBy()).isEqualTo(TEST_AUDITOR_ID);
            softly.assertThat(foundTask.get().getLastModifiedBy()).isEqualTo(TEST_AUDITOR_ID);
            softly.assertThat(foundTask.get().getVersion()).isEqualTo(0L);
        });
    }
}
