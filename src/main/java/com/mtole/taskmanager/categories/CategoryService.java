package com.mtole.taskmanager.categories;

import com.mtole.taskmanager.categories.dto.CategoryCreateRequest;
import com.mtole.taskmanager.common.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    public CategoryService(CategoryRepository categoryRepository, CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    public Category create(CategoryCreateRequest req, Long currentUserId) {
        log.info("Creating category with name={}", req.name());
        Category entity = categoryMapper.toEntity(req);
        entity.setUserId(currentUserId);
        Category saved = categoryRepository.save(entity);
        log.info("Created category with id={}", saved.getId());
        return saved;
    }

    public Category update(Long id, CategoryCreateRequest req, Long currentUserId){
        log.info("Updating category with id={}", id);
        Category existing = categoryRepository.findByIdAndUserId(id, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Category with id=" + id + " not found!"));

        categoryMapper.updateFromRequest(req, existing);
        Category saved = categoryRepository.save(existing);
        log.info("Updated category with id={}", saved.getId());
        return saved;

    }

    public Optional<Category> findById(Long id, Long currentUserId) {
        return categoryRepository.findByIdAndUserId(id, currentUserId);
    }

    public List<Category> findAll(Long currentUserId, int page, int pageSize) {
        return categoryRepository.findAllByUserId(currentUserId, page, pageSize);
    }

    public int countAll(Long currentUserId) {

        return categoryRepository.countByUserId(currentUserId);
    }

    public boolean deleteById(Long id, Long currentUserId) {
        log.info("Deleting category with id={}", id);
        boolean deleted = categoryRepository.deleteByIdAndUserId(id, currentUserId);
        if (deleted) {
            log.info("Deleted category id={}", id);
        } else {
            log.warn("Category with id={} could not be deleted", id);
        }
        return deleted;
    }
}


