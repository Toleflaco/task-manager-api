package com.mtole.taskmanager.tasks;

import com.mtole.taskmanager.categories.Category;
import com.mtole.taskmanager.categories.CategoryRepository;
import com.mtole.taskmanager.tasks.dto.TaskCreateRequest;
import com.mtole.taskmanager.tasks.events.TaskCreatedEvent;
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
        given(categoryRepository.findByIdAndUserId(categoryId,currentUserId)).willReturn(Optional.of(existingCategory));
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
}

