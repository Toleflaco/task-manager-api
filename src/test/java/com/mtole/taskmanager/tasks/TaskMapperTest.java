package com.mtole.taskmanager.tasks;

import com.mtole.taskmanager.categories.Category;
import com.mtole.taskmanager.tasks.dto.TaskCreateRequest;
import com.mtole.taskmanager.tasks.dto.TaskResponse;
import com.mtole.taskmanager.tasks.dto.TaskUpdateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static com.mtole.taskmanager.categories.CategoryTestDataBuilder.aCategory;
import static com.mtole.taskmanager.tasks.TaskTestDataBuilder.aTask;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskMapper")
class TaskMapperTest {

    private final TaskMapper mapper = Mappers.getMapper(TaskMapper.class);

    @Test
    @DisplayName("transforms a task create request to task")
    void toEntity() {

        // Arrange
        TaskCreateRequest request = new TaskCreateRequest("Title task", "Description task", Priority.HIGH,
                OffsetDateTime.of(2026, 6, 29, 15, 0, 0, 0, ZoneOffset.UTC), 12L);

        // Act
        Task result = mapper.toEntity(request);

        // Asserts

        assertThat(result.getId()).isNull();
        assertThat(result.getCreatedAt()).isNull();
        assertThat(result.getUpdatedAt()).isNull();
        assertThat(result.getVersion()).isNull();
        assertThat(result.getUser()).isNull();
        assertThat(result.getCompletedAt()).isNull();
        assertThat(result.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(result.getCategory()).isNull();
        assertThat(result.getTitle()).isEqualTo(request.title());
        assertThat(result.getDescription()).isEqualTo(request.description());
        assertThat(result.getPriority()).isEqualTo(request.priority());
        assertThat(result.getDueDate()).isEqualTo(request.dueDate());

    }

    @Test
    @DisplayName("transforms a task create request to task with optional fields")
    void toEntity_withOptionalFields() {

        // Arrange
        TaskCreateRequest request = new TaskCreateRequest("Title task", null, null, null, null);
        // Act
        Task result = mapper.toEntity(request);

        // Asserts
        assertThat(result.getDescription()).isNull();
        assertThat(result.getCategory()).isNull();
        assertThat(result.getTitle()).isEqualTo(request.title());
        assertThat(result.getDueDate()).isNull();
    }

    @Test
    @DisplayName("transforms task to task response")
    void toResponse() {

        //Arrange
        Category category = aCategory().withId(1L).withName("category name").build();
        Task task = aTask().withId(2L).withTitle("Title task").withStatus(TaskStatus.IN_PROGRESS).withCategory(category).build();
        // Act
        TaskResponse result = mapper.toResponse(task);

        // Asserts
        assertThat(result.id()).isEqualTo(task.getId());
        assertThat(result.title()).isEqualTo(task.getTitle());
        assertThat(result.status()).isEqualTo(task.getStatus());
        assertThat(result.categoryId()).isEqualTo(task.getCategory().getId());
        assertThat(result.categoryName()).isEqualTo(task.getCategory().getName());

    }

    @Test
    @DisplayName("transforms task to task response when category is null")
    void toResponse_withoutCategory() {

        // Arrange
        Task task = aTask().withId(2L).withTitle("Title task").build();


        // Act
        TaskResponse result = mapper.toResponse(task);

        // Asserts
        assertThat(result.id()).isEqualTo(task.getId());
        assertThat(result.title()).isEqualTo(task.getTitle());
        assertThat(result.status()).isEqualTo(task.getStatus());
        assertThat(result.categoryId()).isNull();
        assertThat(result.categoryName()).isNull();

    }

    @Test
    @DisplayName("updates task with request")
    void updateFromRequest() {

        // Arrange
        Long taskId = 2L;
        TaskStatus statusPrevious = TaskStatus.IN_PROGRESS;
        OffsetDateTime previousCreatedAt = OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);

        Task task = aTask().withId(taskId)
                .withTitle("Title task")
                .withStatus(statusPrevious)
                .build();
        ReflectionTestUtils.setField(task, "createdAt", previousCreatedAt);

        TaskUpdateRequest request = new TaskUpdateRequest("Title task request", "Description request", Priority.LOW, null, null, null);

        // Act
        mapper.updateFromRequest(request, task);

        // Asserts
        assertThat(task.getId()).isEqualTo(taskId);
        assertThat(task.getTitle()).isEqualTo("Title task request");
        assertThat(task.getStatus()).isEqualTo(statusPrevious);
        assertThat(task.getCreatedAt()).isEqualTo(previousCreatedAt);

    }
}
