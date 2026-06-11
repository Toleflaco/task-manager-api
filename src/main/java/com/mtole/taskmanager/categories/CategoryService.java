package com.mtole.taskmanager.categories;

import com.mtole.taskmanager.categories.dto.CategoryCreateRequest;
import com.mtole.taskmanager.common.ResourceNotFoundException;
import com.mtole.taskmanager.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final UserRepository userRepository;
    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    public CategoryService(CategoryRepository categoryRepository, CategoryMapper categoryMapper, UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
        this.userRepository = userRepository;
    }

    @Transactional
    public Category create(CategoryCreateRequest request, Long currentUserId) {
        log.info("Creating category with name={}", request.name());
        Category entity = categoryMapper.toEntity(request);
        entity.setUser(userRepository.getReferenceById(currentUserId));
        Category saved = categoryRepository.save(entity);
        log.info("Created category with id={}", saved.getId());
        return saved;
    }

    @Transactional
    public Category update(Long id, CategoryCreateRequest request, Long currentUserId) {
        log.info("Updating category with id={}", id);
        Category existing = categoryRepository.findByIdAndUserId(id, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Category with id=" + id + " not found"));

        categoryMapper.updateFromRequest(request, existing);
        Category saved = categoryRepository.save(existing);
        log.info("Updated category with id={}", saved.getId());
        return saved;

    }
    @Transactional(readOnly = true)
    public Optional<Category> findById(Long id, Long currentUserId) {
        return categoryRepository.findByIdAndUserId(id, currentUserId);
    }
    @Transactional(readOnly = true)
    public Page<Category> findAll(Long currentUserId, Pageable pageable) {
        return categoryRepository.findAllByUserId(currentUserId, pageable);
    }
    @Transactional(readOnly = true)
    public long countAll(Long currentUserId) {

        return categoryRepository.countByUserId(currentUserId);
    }

    @Transactional
    public boolean deleteById(Long id, Long currentUserId) {
        log.info("Deleting category with id={}", id);
        long deleted = categoryRepository.deleteByIdAndUserId(id, currentUserId);
        if (deleted > 0) {
            log.info("Deleted category id={}", id);
            return true;
        } else {
            log.warn("Category with id={} not found or not owned by user={}", id, currentUserId);
            return false;
        }
    }
}


