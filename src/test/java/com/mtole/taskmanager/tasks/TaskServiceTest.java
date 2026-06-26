package com.mtole.taskmanager.tasks;

import com.mtole.taskmanager.categories.Category;
import com.mtole.taskmanager.categories.CategoryRepository;
import com.mtole.taskmanager.common.ResourceNotFoundException;
import com.mtole.taskmanager.tasks.dto.TaskCreateRequest;
import com.mtole.taskmanager.tasks.events.TaskCreatedEvent;
import com.mtole.taskmanager.tasks.events.TaskDeletedEvent;
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

import java.util.Optional;

import static com.mtole.taskmanager.categories.CategoryTestDataBuilder.aCategory;
import static com.mtole.taskmanager.tasks.TaskTestDataBuilder.aTask;
import static com.mtole.taskmanager.users.UserTestDataBuilder.aUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

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
}
