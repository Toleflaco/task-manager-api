package com.mtole.taskmanager.tasks;

import com.mtole.taskmanager.categories.Category;
import com.mtole.taskmanager.categories.CategoryRepository;
import com.mtole.taskmanager.common.ResourceNotFoundException;
import com.mtole.taskmanager.tasks.dto.TaskCreateRequest;
import com.mtole.taskmanager.tasks.dto.TaskStatsResponse;
import com.mtole.taskmanager.tasks.dto.TaskSummaryProjection;
import com.mtole.taskmanager.tasks.dto.TaskUpdateRequest;
import com.mtole.taskmanager.tasks.events.TaskCreatedEvent;
import com.mtole.taskmanager.tasks.events.TaskDeletedEvent;
import com.mtole.taskmanager.tasks.events.TaskStatusChangedEvent;
import com.mtole.taskmanager.tasks.events.TaskUpdatedEvent;
import com.mtole.taskmanager.users.User;
import com.mtole.taskmanager.users.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.mtole.taskmanager.categories.CategoryTestDataBuilder.aCategory;
import static com.mtole.taskmanager.tasks.TaskTestDataBuilder.aTask;
import static com.mtole.taskmanager.users.UserTestDataBuilder.aUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskService")
public class TaskServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private TaskMapper taskMapper;

    @InjectMocks
    private TaskService taskService;


    @Test
    @DisplayName("Create a task with categoryId null returns a task and publishes a created event")
    void create_withCategoryIdNull_returnsTaskAndPublishesCreatedEvent() {

        // Arrange

        Long currentUserId = 1L;
        Long taskId = 42L;
        String title = "Study with Claude";

        User existingUser = aUser().withId(currentUserId).build();
        Task task = aTask().withId(taskId).withTitle(title).build();
        TaskCreateRequest request = new TaskCreateRequest(title, null, Priority.HIGH, null, null);


        given(taskMapper.toEntity(request)).willReturn(task);
        given(userRepository.getReferenceById(currentUserId)).willReturn(existingUser);
        given(taskRepository.save(task)).willReturn(task);

        // Act
        Task result = taskService.create(request, currentUserId);

        // Asserts

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(taskId);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(result.getUser()).isEqualTo(existingUser);
        assertThat(result.getCategory()).isNull();

        ArgumentCaptor<TaskCreatedEvent> eventCaptor = ArgumentCaptor.forClass(TaskCreatedEvent.class);
        then(applicationEventPublisher).should().publishEvent(eventCaptor.capture());

        TaskCreatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.taskId()).isEqualTo(taskId);
        assertThat(publishedEvent.userId()).isEqualTo(currentUserId);
        assertThat(publishedEvent.title()).isEqualTo(title);
        assertThat(publishedEvent.status()).isEqualTo("PENDING");
        assertThat(publishedEvent.categoryId()).isNull();
    }

    @Test
    @DisplayName("Create a task with categoryId returns a task and publishes a created event")
    void create_withCategoryId_returnsTaskAndPublishesCreatedEvent() {

        // Arrange

        Long currentUserId = 1L;
        Long taskId = 42L;
        Long categoryId = 7L;
        String title = "Study with Claude";
        User existingUser = aUser().withId(currentUserId).build();
        Category existingCategory = aCategory().withId(categoryId).build();
        Task task = aTask().withId(taskId).withTitle(title).build();
        TaskCreateRequest request = new TaskCreateRequest(title, null, Priority.HIGH, null, categoryId);

        given(taskMapper.toEntity(request)).willReturn(task);
        given(userRepository.getReferenceById(currentUserId)).willReturn(existingUser);
        given(categoryRepository.findByIdAndUserId(categoryId, currentUserId)).willReturn(Optional.of(existingCategory));
        given(taskRepository.save(task)).willReturn(task);

        // Act
        Task result = taskService.create(request, currentUserId);

        // Assert

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(taskId);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(result.getUser()).isEqualTo(existingUser);
        assertThat(result.getCategory()).isEqualTo(existingCategory);

        ArgumentCaptor<TaskCreatedEvent> eventCaptor = ArgumentCaptor.forClass(TaskCreatedEvent.class);
        then(applicationEventPublisher).should().publishEvent(eventCaptor.capture());

        TaskCreatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.taskId()).isEqualTo(taskId);
        assertThat(publishedEvent.userId()).isEqualTo(currentUserId);
        assertThat(publishedEvent.title()).isEqualTo(title);
        assertThat(publishedEvent.status()).isEqualTo("PENDING");
        assertThat(publishedEvent.categoryId()).isEqualTo(categoryId);
    }

    @Test
    @DisplayName("deletes task and publishes deleted event when task existes")
    void deleteById_withExistingTask_deletesTaskAndPublishesDeletedEvent() {

        // Arrange
        Long currentUserId = 1L;
        Long taskId = 42L;
        User existingUser = aUser().withId(currentUserId).build();
        Task existingTask = aTask().withId(taskId).withUser(existingUser).build();
        String title = existingTask.getTitle();
        String status = existingTask.getStatus().name();

        given(taskRepository.findByIdAndUserId(taskId, currentUserId)).willReturn(Optional.of(existingTask));

        // Act
        boolean result = taskService.deleteById(taskId, currentUserId);

        // Asserts

        assertThat(result).isTrue();
        ArgumentCaptor<TaskDeletedEvent> eventCaptor = ArgumentCaptor.forClass(TaskDeletedEvent.class);

        then(applicationEventPublisher).should().publishEvent(eventCaptor.capture());

        TaskDeletedEvent deletedEvent = eventCaptor.getValue();
        assertThat(deletedEvent.taskId()).isEqualTo(taskId);
        assertThat(deletedEvent.userId()).isEqualTo(currentUserId);
        assertThat(deletedEvent.title()).isEqualTo(title);
        assertThat(deletedEvent.status()).isEqualTo(status);

    }

    @Test
    @DisplayName("returns false and publishes no event when task does not exist")
    void deleteById_withNonExistingTask_returnsFalseAndPublishesNoEvent() {

        // Arrange

        Long currentUserId = 1L;
        Long taskId = 99L;

        given(taskRepository.findByIdAndUserId(taskId, currentUserId)).willReturn(Optional.empty());

        // Act
        boolean result = taskService.deleteById(taskId, currentUserId);

        // Asserts
        assertThat(result).isFalse();
        then(applicationEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("throws resource not found exception and publishes no event when task not found")
    void complete_withNonExistingTask_throwsResourceNotFoundExceptionAndPublishesNoEvent() {

        // Arrange

        Long currentUserId = 1L;
        Long taskId = 99L;

        given(taskRepository.findByIdAndUserId(taskId, currentUserId)).willReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> taskService.complete(taskId, currentUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Task with id=" + taskId + " not found");

        // Then (efecto secundario)

        then(applicationEventPublisher).shouldHaveNoInteractions();

    }

    @Test
    @DisplayName("throws invalid task state exception and publishes no event when task is in a final state")
    void complete_withTaskInFinalState_throwsInvalidTaskStateExceptionAndPublishesNoEvent() {

        // Arrange
        Long currentUserId = 1L;
        Long taskId = 99L;
        Task existingTask = aTask().withId(taskId).withStatus(TaskStatus.COMPLETED).build();

        given(taskRepository.findByIdAndUserId(taskId, currentUserId)).willReturn(Optional.of(existingTask));

        // Act + Assert
        assertThatThrownBy(() -> taskService.complete(taskId, currentUserId))
                .isInstanceOf(InvalidTaskStateException.class)
                .hasMessage("Cannot complete task with id=" + taskId + ", current status is COMPLETED");

        // Then (efecto secundario)
        then(applicationEventPublisher).shouldHaveNoInteractions();

    }

    @Test
    @DisplayName("completes task and publishes task status change event when task exists")
    void complete_withExistingTask_completesTaskAndPublishesTaskStatusChangedEvent() {

        // Arrange
        Long currentUserId = 1L;
        Long taskId = 99L;
        TaskStatus currentStatus = TaskStatus.PENDING;
        Task existingTask = aTask().withId(taskId).withStatus(currentStatus).build();

        given(taskRepository.findByIdAndUserId(taskId, currentUserId)).willReturn(Optional.of(existingTask));
        given(taskRepository.save(existingTask)).willReturn(existingTask);

        // Act
        Task result = taskService.complete(taskId, currentUserId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        ArgumentCaptor<TaskStatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(TaskStatusChangedEvent.class);
        then(applicationEventPublisher).should().publishEvent(eventCaptor.capture());
        TaskStatusChangedEvent event = eventCaptor.getValue();
        assertThat(event.taskId()).isEqualTo(taskId);
        assertThat(event.userId()).isEqualTo(currentUserId);
        assertThat(event.oldStatus()).isEqualTo(TaskStatus.PENDING.name());
        assertThat(event.newStatus()).isEqualTo(TaskStatus.COMPLETED.name());

    }

    @Test
    @DisplayName("throws resource not found exception and publishes no event when task not found")
    void cancel_withNonExistingTask_throwsResourceNotFoundExceptionAndPublishesNoEvent() {

        // Arrange

        Long currentUserId = 1L;
        Long taskId = 99L;

        given(taskRepository.findByIdAndUserId(taskId, currentUserId)).willReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> taskService.cancel(taskId, currentUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Task with id=" + taskId + " not found");

        // Then (efecto secundario)

        then(applicationEventPublisher).shouldHaveNoInteractions();

    }

    @Test
    @DisplayName("throws invalid task state exception and publishes no event when task is in a final state")
    void cancel_withTaskInFinalState_throwsInvalidTaskStateExceptionAndPublishesNoEvent() {

        // Arrange
        Long currentUserId = 1L;
        Long taskId = 99L;
        Task existingTask = aTask().withId(taskId).withStatus(TaskStatus.COMPLETED).build();

        given(taskRepository.findByIdAndUserId(taskId, currentUserId)).willReturn(Optional.of(existingTask));

        // Act + Assert
        assertThatThrownBy(() -> taskService.cancel(taskId, currentUserId))
                .isInstanceOf(InvalidTaskStateException.class)
                .hasMessage("Cannot cancel task with id=" + taskId + ", current status is COMPLETED");

        // Then (efecto secundario)
        then(applicationEventPublisher).shouldHaveNoInteractions();

    }

    @Test
    @DisplayName("cancels task and publishes task status change event when task exists")
    void cancel_withExistingTask_cancelsTaskAndPublishesTaskStatusChangedEvent() {

        // Arrange
        Long currentUserId = 1L;
        Long taskId = 99L;
        TaskStatus currentStatus = TaskStatus.PENDING;
        Task existingTask = aTask().withId(taskId).withStatus(currentStatus).build();

        given(taskRepository.findByIdAndUserId(taskId, currentUserId)).willReturn(Optional.of(existingTask));
        given(taskRepository.save(existingTask)).willReturn(existingTask);

        // Act
        Task result = taskService.cancel(taskId, currentUserId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        ArgumentCaptor<TaskStatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(TaskStatusChangedEvent.class);
        then(applicationEventPublisher).should().publishEvent(eventCaptor.capture());
        TaskStatusChangedEvent event = eventCaptor.getValue();
        assertThat(event.taskId()).isEqualTo(taskId);
        assertThat(event.userId()).isEqualTo(currentUserId);
        assertThat(event.oldStatus()).isEqualTo(TaskStatus.PENDING.name());
        assertThat(event.newStatus()).isEqualTo(TaskStatus.CANCELLED.name());

    }

    @Test
    @DisplayName("throws resource not found exception and publishes no event when task not found")
    void update_withNonExistingTask_throwsResourceNotFoundExceptionAndPublishesNoEvent() {

        // Arrange
        Long currentUserId = 1L;
        Long taskId = 99L;
        TaskUpdateRequest request = new TaskUpdateRequest("Task title", null, Priority.HIGH, null, null, null);
        given(taskRepository.findByIdAndUserId(taskId, currentUserId)).willReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> taskService.update(taskId, request, currentUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Task with id=" + taskId + " not found");

        // Then (efecto secundario)
        then(applicationEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("throws resource not found exception and publishes no event when category not found")
    void update_withExistingTaskAndNonExistingCategory_throwsResourceNotFoundExceptionAndPublishesNoEvent() {

        // Arrange
        Long currentUserId = 1L;
        Long taskId = 99L;
        Long categoryId = 1L;
        TaskUpdateRequest request = new TaskUpdateRequest("Task title", null, Priority.HIGH, null, categoryId, null);
        Task existingTask = aTask().withId(taskId).build();
        given(taskRepository.findByIdAndUserId(taskId, currentUserId)).willReturn(Optional.of(existingTask));
        given(categoryRepository.findByIdAndUserId(categoryId, currentUserId)).willReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> taskService.update(taskId, request, currentUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Category with id=" + categoryId + " not found");

        // Then (efecto secundario)
        then(applicationEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("throws optimistic locking failure exception and publishes no event when version mismatch")
    void update_withVersionMismatch_throwsOptimisticLockingFailureExceptionAndPublishesNoEvent() {

        // Arrange
        Long currentUserId = 1L;
        Long taskId = 99L;
        TaskUpdateRequest request = new TaskUpdateRequest("Task title", null, Priority.HIGH, null, null, 2L);
        Task existingTask = aTask().withId(taskId).build();
        ReflectionTestUtils.setField(existingTask, "version", 1L);
        given(taskRepository.findByIdAndUserId(taskId, currentUserId)).willReturn(Optional.of(existingTask));

        // Act + Assert
        assertThatThrownBy(() -> taskService.update(taskId, request, currentUserId))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessage("Task " + taskId + " was modified by another request");

        // Then (efecto secundario)
        then(applicationEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("updates task and publishes task update event when task exists")
    void update_withExistingTask_updatesTaskAndPublishesTaskUpdatedEvent() {

        // Arrange
        Long currentUserId = 1L;
        Long taskId = 99L;
        Long categoryId = 1L;
        Task existingTask = aTask().withId(taskId).build();
        ReflectionTestUtils.setField(existingTask, "version", 1L);
        Category existingCategory = aCategory().withId(categoryId).build();
        TaskUpdateRequest request = new TaskUpdateRequest("Task title", null, Priority.HIGH, null, categoryId, 1L);

        given(taskRepository.findByIdAndUserId(taskId, currentUserId)).willReturn(Optional.of(existingTask));
        given(categoryRepository.findByIdAndUserId(categoryId, currentUserId)).willReturn(Optional.of(existingCategory));
        given(taskRepository.save(existingTask)).willReturn(existingTask);


        // Act
        Task result = taskService.update(taskId, request, currentUserId);

        // Assert
        assertThat(result).isSameAs(existingTask);  // save devuelve la misma instancia
        assertThat(result.getCategory()).isEqualTo(existingCategory);  // el servicio asignó la categoría

        then(taskMapper).should().updateFromRequest(request, existingTask);

        ArgumentCaptor<TaskUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(TaskUpdatedEvent.class);
        then(applicationEventPublisher).should().publishEvent(eventCaptor.capture());
        TaskUpdatedEvent event = eventCaptor.getValue();
        assertThat(event.taskId()).isEqualTo(taskId);
        assertThat(event.userId()).isEqualTo(currentUserId);
    }

    @Test
    @DisplayName("finds task by id and returns it when task exists")
    void findById_withTaskExisting_returnsTask() {

        // Arrange
        Long currentUserId = 1L;
        Long taskId = 99L;
        Task existingTask = aTask().withId(taskId).build();

        given(taskRepository.findByIdAndUserId(taskId, currentUserId)).willReturn(Optional.of(existingTask));

        // Act
        Optional<Task> result = taskService.findById(taskId, currentUserId);

        // Asserts
        assertThat(result).contains(existingTask);
    }

    @Test
    @DisplayName("finds task by id and returns empty optional when task does not exist")
    void findById_withNonExistingTask_returnsEmptyOptional() {

        // Arrange
        Long currentUserId = 1L;
        Long taskId = 99L;

        given(taskRepository.findByIdAndUserId(taskId, currentUserId)).willReturn(Optional.empty());

        // Act
        Optional<Task> result = taskService.findById(taskId, currentUserId);

        // Asserts
        assertThat(result).isEmpty();

    }

    @Test
    @DisplayName("gets stats by user id returns stats in task stats response")
    void getStats_withUserId_returnsStats() {

        // Arrange
        Long currentUserId = 1L;
        TaskStatsResponse taskStatsResponse = new TaskStatsResponse(1L,1L,2L,3L,5L);

        given(taskRepository.findStatsByUserId(currentUserId)).willReturn(taskStatsResponse);

        // Act
        TaskStatsResponse result = taskService.getStats(currentUserId);

        // Asserts
        assertThat(result).isSameAs(taskStatsResponse);
    }


    @Test
    @DisplayName("finds all tasks and returns page when filter is empty")
    void findAll_withAllFiltersNull_returnsPageFromRepository() {

        // Arrange
        Long currentUserId = 1L;
        TaskFilter filter = new TaskFilter(null, null, null);
        Pageable pageable = PageRequest.of(0, 10);

        TaskSummaryProjection mockProjection = mock(TaskSummaryProjection.class);
        Page<TaskSummaryProjection> expectedPage = new PageImpl<>(List.of(mockProjection));

        given(taskRepository.findAllSummariesBy(any(Specification.class), eq(pageable)))
                .willReturn(expectedPage);

        // Act
        Page<TaskSummaryProjection> result = taskService.findAll(currentUserId, filter, pageable);

        // Assert
        assertThat(result).isEqualTo(expectedPage);
    }
}
