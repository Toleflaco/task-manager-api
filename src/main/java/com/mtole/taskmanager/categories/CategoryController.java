package com.mtole.taskmanager.categories;

import com.mtole.taskmanager.categories.dto.CategoryCreateRequest;
import com.mtole.taskmanager.categories.dto.CategoryResponse;
import com.mtole.taskmanager.common.ResourceNotFoundException;
import com.mtole.taskmanager.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/categories")
@Tag(name = "Categories", description = "CRUD category")
public class CategoryController {
    private final CategoryService categoryService;
    private final CategoryMapper categoryMapper;

    public CategoryController(CategoryService categoryService, CategoryMapper categoryMapper) {
        this.categoryService = categoryService;
        this.categoryMapper = categoryMapper;
    }

    @Operation(summary = "Create a new category", description = "Create a category with unique name")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "category created"),
            @ApiResponse(responseCode = "400", description = "Invalid input data", content = @Content)
    })
    @PostMapping
    public ResponseEntity<CategoryResponse> addCategory(@Valid @RequestBody CategoryCreateRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        Category created = categoryService.create(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryMapper.toResponse(created));
    }

    @Operation(summary = "List all categories for currentUserId", description = "List all categories by page")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List all categories for currentUserId"),
            @ApiResponse(responseCode = "400", description = "Header not valid", content = @Content)
    })
    @GetMapping
    public Map<String, Object> findAll(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int pageSize) {
        Long currentUserId = SecurityUtils.currentUserId();
        List<Category> categories = categoryService.findAll(currentUserId, page, pageSize);
        List<CategoryResponse> categoriesResponse = categories.stream().map(categoryMapper::toResponse)
                .toList();
        int totalElements = categoryService.countAll(currentUserId);
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        return Map.of(
                "content", categoriesResponse,
                "page", page,
                "pageSize", pageSize,
                "totalElements", totalElements,
                "totalPages", totalPages
        );
    }

    @Operation(summary = "Search for a category by ID", description = "Searches for a category by ID; if it is not found, it returns an exception")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category found"),
            @ApiResponse(responseCode = "404", description = "Category not found", content = @Content)
    })
    @GetMapping("/{id}")
    public CategoryResponse findCategoryById(@PathVariable Long id) {
        Long currentUserId = SecurityUtils.currentUserId();
        return categoryService.findById(id, currentUserId)
                .map(categoryMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id " + id));
    }

    @Operation(summary = "Update category with id", description = "Update category with id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category updated"),
            @ApiResponse(responseCode = "404", description = "Category not update", content = @Content)
    })
    @PutMapping("/{id}")
    public CategoryResponse updateCategoryById(@PathVariable Long id,
                                               @Valid @RequestBody CategoryCreateRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        return categoryMapper.toResponse(categoryService.update(id, request, currentUserId));
    }

    @Operation(summary = "Delete a category", description = "Delete a category with id")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Delete category"),
            @ApiResponse(responseCode = "404", description = "The category could not be deleted", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategoryById(@PathVariable Long id) {
        Long currentUserId = SecurityUtils.currentUserId();
        if (!categoryService.deleteById(id, currentUserId)) {
            throw new ResourceNotFoundException("Category not found with id " + id);
        }
        return ResponseEntity.noContent().build();
    }


}
