package com.mtole.taskmanager.tasks;

import com.mtole.taskmanager.categories.Category;
import com.mtole.taskmanager.config.TestAuditorConfig;
import com.mtole.taskmanager.users.User;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.SoftAssertions;
import org.hibernate.annotations.processing.SQL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.domain.Specification;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Optional;

import static com.mtole.taskmanager.categories.CategoryTestDataBuilder.aCategory;
import static com.mtole.taskmanager.config.TestAuditorConfig.TEST_AUDITOR_ID;
import static com.mtole.taskmanager.tasks.TaskSpecifications.*;
import static com.mtole.taskmanager.tasks.TaskStatus.COMPLETED;
import static com.mtole.taskmanager.tasks.TaskStatus.PENDING;
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

    @Test
    @DisplayName("finds task with user and status spec")
    void findAll_withUserAndStatusSpec_returnsOnlyMatchingTasks() {

        // Arrange
        User user1 = aUser().withEmail("u1@example.com").build();
        entityManager.persistAndFlush(user1);
        User user2 = aUser().withEmail("u2@example.com").build();
        entityManager.persistAndFlush(user2);
        Task t1 = aTask().withUser(user1).withStatus(PENDING).withTitle("target").build();
        Task t2 = aTask().withUser(user1).withStatus(COMPLETED).withTitle("wrong-status").build();
        Task t3 = aTask().withUser(user2).withStatus(PENDING).withTitle("wrong-user").build();

        taskRepository.saveAndFlush(t1);
        taskRepository.saveAndFlush(t2);
        taskRepository.saveAndFlush(t3);
        entityManager.clear();

        // Act
        Specification<Task> spec = byUserId(user1.getId()).and(byStatus(PENDING));
        List<Task> result = taskRepository.findAll(spec);

        // Asserts
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result)
                    .hasSize(1)
                    .extracting(Task::getTitle)
                    .containsExactly("target");

        });
    }

    @Test
    @DisplayName("finds task with user and status and category name spec")
    void findAll_withUserStatusAndCategoryNameSpec_returnsOnlyMatchingTasks() {

        // Arrange
        User u1 = aUser().withEmail("u1@example.com").build();
        User u2 = aUser().withEmail("u2@example.com").build();
        entityManager.persistAndFlush(u1);
        entityManager.persistAndFlush(u2);
        Category c1 = aCategory().withName("work").withUser(u1).build();
        Category c2 = aCategory().withName("personal").withUser(u1).build();
        Category c3 = aCategory().withName("work").withUser(u2).build();
        entityManager.persistAndFlush(c1);
        entityManager.persistAndFlush(c2);
        entityManager.persistAndFlush(c3);
        Task t1 = aTask().withUser(u1).withStatus(PENDING).withCategory(c1).withTitle("target").build();
        Task t2 = aTask().withUser(u1).withStatus(COMPLETED).withCategory(c2).withTitle("wrong-category-name").build();
        Task t3 = aTask().withUser(u2).withStatus(PENDING).withCategory(c3).withTitle("wrong-user").build();
        Task t4 = aTask().withUser(u1).withStatus(PENDING).withCategory(c2).withTitle("wrong-category").build();

        taskRepository.saveAndFlush(t1);
        taskRepository.saveAndFlush(t2);
        taskRepository.saveAndFlush(t3);
        taskRepository.saveAndFlush(t4);
        entityManager.clear();

        // Act
        Specification<Task> spec = byUserId(u1.getId()).and(byStatus(PENDING)).and(byCategoryName("work"));
        List<Task> result = taskRepository.findAll(spec);

        // Asserts
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result)
                    .hasSize(1)
                    .extracting(Task::getTitle)
                    .containsExactly("target");
        });
    }

    @Test
    @DisplayName("deletes task sets deleted at without removing row")
    void deleteTask_setsDeletedAtWithoutRemovingRow() {
        // Arrange
        User u1 = aUser().withEmail("u1@example.com").build();
        entityManager.persistAndFlush(u1);
        Task t1 = aTask().withUser(u1).withStatus(PENDING).withTitle("target").build();
        taskRepository.saveAndFlush(t1);
        Long taskId = t1.getId();
        entityManager.clear();

        // Act
        taskRepository.deleteById(t1.getId());
        entityManager.flush();
        // Asserts

        // Assert 1 — la fila existe físicamente con deleted_at != null (nativeQuery)
        Object deletedAt = entityManager.getEntityManager()
                .createNativeQuery("SELECT deleted_at FROM tasks WHERE id = ?")
                .setParameter(1, taskId)
                .getSingleResult();

        // Assert 2 — findById devuelve Optional.empty() por @SQLRestriction
        Optional<Task> foundViaOrm = taskRepository.findById(taskId);

        SoftAssertions.assertSoftly(softly -> {
           softly.assertThat(deletedAt).isNotNull();
            softly.assertThat(foundViaOrm).isEmpty();
        });
    }

    @Test
    @DisplayName("filters out soft-deleted tasks from findAll results")
    void findAll_withSoftDeletedTask_excludesTaskFromResult() {
        // Arrange
        User u1 = aUser().withEmail("u1@example.com").build();
        entityManager.persistAndFlush(u1);
        Task t1 = aTask().withUser(u1).withStatus(PENDING).withTitle("target").build();
        taskRepository.saveAndFlush(t1);
        Task t2 = aTask().withUser(u1).withStatus(PENDING).withTitle("other").build();
        taskRepository.saveAndFlush(t2);
        entityManager.clear();
        taskRepository.deleteById(t2.getId());
        entityManager.flush();

        //Act
        Specification<Task> spec = byUserId(u1.getId());
        List<Task> result = taskRepository.findAll(spec);

        // Asserts
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result)
                    .hasSize(1)
                    .extracting(Task::getTitle)
                    .containsExactly("target");
        });


    }
}
