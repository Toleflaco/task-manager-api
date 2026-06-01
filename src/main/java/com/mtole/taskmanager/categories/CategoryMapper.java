package com.mtole.taskmanager.categories;

import com.mtole.taskmanager.categories.dto.CategoryCreateRequest;
import com.mtole.taskmanager.categories.dto.CategoryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel="spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CategoryMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "userId", ignore = true)
    Category toEntity(CategoryCreateRequest req);

    CategoryResponse toResponse(Category category);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "userId", ignore = true)
    void updateFromRequest(CategoryCreateRequest req, @MappingTarget Category entity);
}
