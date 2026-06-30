package com.mtole.taskmanager.categories;

import com.mtole.taskmanager.categories.dto.CategoryCreateRequest;
import com.mtole.taskmanager.categories.dto.CategoryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static com.mtole.taskmanager.categories.CategoryTestDataBuilder.aCategory;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CategoryMapper")
class CategoryMapperTest {

    private final CategoryMapper categoryMapper = Mappers.getMapper(CategoryMapper.class);

    @Test
    @DisplayName("transforms a category create request to category")
    void toEntity(){

        // Arrange
        CategoryCreateRequest request = new CategoryCreateRequest("Category name","Description category");

        // Act
        Category result = categoryMapper.toEntity(request);

        // Asserts
        assertThat(result.getId()).isNull();
        assertThat(result.getCreatedAt()).isNull();
        assertThat(result.getUpdatedAt()).isNull();
        assertThat(result.getUser()).isNull();
        assertThat(result.getVersion()).isNull();
        assertThat(result.getName()).isEqualTo(request.name());
        assertThat(result.getDescription()).isEqualTo(request.description());

    }

    @Test
    @DisplayName("transforms a category create request to category when description is null")
    void toEntity_withOptionalDescription(){

        // Arrange
        CategoryCreateRequest request = new CategoryCreateRequest("Category name",null);

        // Act

        Category result = categoryMapper.toEntity(request);

        // Asserts

        assertThat(result.getName()).isEqualTo(request.name());
        assertThat(result.getDescription()).isNull();

    }

    @Test
    @DisplayName("transforms category to category response ")
    void toResponse(){

        // Arrange
        Category category = aCategory().withId(1L).build();

        // Act
        CategoryResponse result = categoryMapper.toResponse(category);

        // Asserts
        assertThat(result.createdAt()).isNull();
        assertThat(result.updatedAt()).isNull();
        assertThat(result.id()).isEqualTo(category.getId());
        assertThat(result.name()).isEqualTo(category.getName());
        assertThat(result.description()).isEqualTo(category.getDescription());

    }

    @Test
    @DisplayName("updates category with request")
    void updateFromRequest(){

        // Arrange
        Long categoryId = 1L;
        OffsetDateTime previousCreatedAt = OffsetDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        Category category = aCategory().withId(categoryId).build();
        ReflectionTestUtils.setField(category, "createdAt", previousCreatedAt);

        CategoryCreateRequest request = new CategoryCreateRequest("Category name","Description category");

        // Act
        categoryMapper.updateFromRequest(request,category);

        // Asserts
        assertThat(category.getId()).isEqualTo(categoryId);
        assertThat(category.getCreatedAt()).isEqualTo(previousCreatedAt);
        assertThat(category.getName()).isEqualTo("Category name");
        assertThat(category.getDescription()).isEqualTo("Description category");
    }

}


