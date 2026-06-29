package com.mtole.taskmanager.categories;

import com.mtole.taskmanager.categories.dto.CategoryCreateRequest;
import com.mtole.taskmanager.categories.events.CategoryCreatedEvent;
import com.mtole.taskmanager.common.ResourceNotFoundException;
import com.mtole.taskmanager.tasks.TaskMapper;
import com.mtole.taskmanager.tasks.TaskRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static com.mtole.taskmanager.categories.CategoryTestDataBuilder.aCategory;
import static com.mtole.taskmanager.users.UserTestDataBuilder.aUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService")
public class CategoryServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private CategoryMapper categoryMapper;


    @InjectMocks
    private CategoryService categoryService;

    @Test
    @DisplayName("finds category by id and returns it when category exists")
    void findById_withExistingCategory_returnsCategory() {

        // Arrange
        Long currentUserId = 1L;
        Long categoryId = 1L;
        Category existingCategory = aCategory().withId(categoryId).build();

        given(categoryRepository.findByIdAndUserId(categoryId, currentUserId)).willReturn(Optional.of(existingCategory));

        // Act

        Optional<Category> result = categoryService.findById(categoryId, currentUserId);

        // Asserts

        assertThat(result).contains(existingCategory);
    }

    @Test
    @DisplayName("finds category by id and returns empty optional when category does not exist")
    void findById_withNonExistingCategory_returnsEmptyOptional() {
        // Arrange
        Long currentUserId = 1L;
        Long categoryId = 1L;

        given(categoryRepository.findByIdAndUserId(categoryId, currentUserId)).willReturn(Optional.empty());

        // Act
        Optional<Category> result = categoryService.findById(categoryId, currentUserId);

        // Asserts
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("counts all categories by user id")
    void countAll_withUserId_returnsCount() {
        // Arrange
        Long currentUserId = 1L;
        long expectedCount = 2L;

        given(categoryRepository.countByUserId(currentUserId)).willReturn(2L);

        // Act
        long result = categoryService.countAll(currentUserId);

        // Asserts
        assertThat(result).isEqualTo(expectedCount);

    }

    @Test
    @DisplayName("find all categories and returns page when user id exists")
    void findAll_withUserId_returnsPage() {

        // Arrange
        Long currentUserId = 1L;
        Long categoryId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        Category existingCategory = aCategory().withId(categoryId).build();
        Page<Category> expectedPage = new PageImpl<>(List.of(existingCategory));


        given(categoryRepository.findAllByUserId(currentUserId, pageable)).willReturn(expectedPage);

        // Act
        Page<Category> result = categoryService.findAll(currentUserId, pageable);

        // Asserts
        assertThat(result).isEqualTo(expectedPage);

    }

    @Test
    @DisplayName("creates a category and publishes a created event")
    void create_returnsCategoryAndPublishesCreatedEvent() {

        // Arrange
        Long currentUserId = 1L;
        Long categoryId = 1L;
        String nameCategory = "nameCategory";
        User existingUser = aUser().withId(currentUserId).build();
        Category category = aCategory().withId(categoryId).withName(nameCategory).build();
        CategoryCreateRequest request = new CategoryCreateRequest(nameCategory, null);

        given(categoryMapper.toEntity(request)).willReturn(category);
        given(userRepository.getReferenceById(currentUserId)).willReturn(existingUser);
        given(categoryRepository.save(category)).willReturn(category);

        // Act

        Category result = categoryService.create(request, currentUserId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(categoryId);
        assertThat(result.getName()).isEqualTo(nameCategory);
        assertThat(result.getUser()).isEqualTo(existingUser);

        ArgumentCaptor<CategoryCreatedEvent> argumentCaptor = ArgumentCaptor.forClass(CategoryCreatedEvent.class);
        then(applicationEventPublisher).should().publishEvent(argumentCaptor.capture());

        CategoryCreatedEvent publishedEvent = argumentCaptor.getValue();
        assertThat(publishedEvent.categoryId()).isEqualTo(categoryId);
        assertThat(publishedEvent.name()).isEqualTo(nameCategory);
        assertThat(publishedEvent.userId()).isEqualTo(currentUserId);

    }

    @Test
    @DisplayName("throws resource not found exception and publishes no event when category not found")
    void update_withNonExistingCategory_throwsResourceNotFoundExceptionAndPublishesNoEvent() {
        // Arrange
        Long currentUserId = 1L;
        Long categoryId = 1L;
        Category existingCategory = aCategory().withId(categoryId).build();
        CategoryCreateRequest request = new CategoryCreateRequest("Category name", null);
        given(categoryRepository.findByIdAndUserId(categoryId, currentUserId)).willReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> categoryService.update(categoryId, request, currentUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Category with id=" + categoryId + " not found");

        // Then (efecto secundario)
        then(applicationEventPublisher).shouldHaveNoInteractions();


    }
}
