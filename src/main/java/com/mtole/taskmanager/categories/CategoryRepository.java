package com.mtole.taskmanager.categories;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    Category save(Category category);
    Optional<Category> findByIdAndUserId(Long id,Long userId);
    List<Category> findAllByUserId(Long userId, int page, int pageSize);
    int countByUserId(Long userId);
    boolean deleteByIdAndUserId(Long id, Long userId);
    int deleteAllByUserId(Long userId);
}
