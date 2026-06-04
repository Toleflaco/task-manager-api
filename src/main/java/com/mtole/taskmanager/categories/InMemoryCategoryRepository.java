package com.mtole.taskmanager.categories;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryCategoryRepository implements CategoryRepository {

    private final Map<Long, Category> categories = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong();

    @Override
    public Category save(Category category) {
        if (category.getId() == null) {
            category.setId(counter.incrementAndGet());
        }
        if (category.getCreatedAt() == null) {
            category.setCreatedAt(LocalDateTime.now());
        }
        categories.put(category.getId(), category);
        return category;
    }

    @Override
    public Optional<Category> findByIdAndUserId(Long id, Long userId) {
        return Optional.ofNullable(categories.get(id))
                .filter(category -> category.getUserId().equals(userId));
    }

    @Override
    public List<Category> findAllByUserId(Long userId, int page, int pageSize) {
        return categories.values().stream()
                .filter(category -> category.getUserId().equals(userId))
                .skip((long) page * pageSize)
                .limit(pageSize)
                .toList();
    }

    @Override
    public int countByUserId(Long userId) {
        return (int) categories.values().stream()
                .filter(category -> category.getUserId().equals(userId))
                .count();
    }

    @Override
    public boolean deleteByIdAndUserId(Long id, Long userId) {
        return categories.values().removeIf(category -> category.getUserId().equals(userId) && category.getId().equals(id));
    }

    @Override
    public int deleteAllByUserId(Long userId) {
        int sizeBefore = categories.size();
        categories.values().removeIf(category -> userId.equals(category.getUserId()));
        return sizeBefore - categories.size();
    }
}
