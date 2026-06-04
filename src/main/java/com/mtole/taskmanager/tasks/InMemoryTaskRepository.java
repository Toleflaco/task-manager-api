package com.mtole.taskmanager.tasks;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository

public class InMemoryTaskRepository implements TaskRepository {

    private final Map<Long, Task> tasks = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong();

    @Override
    public Task save(Task task) {
        if (task.getId() == null) {
            task.setId(counter.incrementAndGet());
        }
        if (task.getCreatedAt() == null) {
            task.setCreatedAt(LocalDateTime.now());
        }
        tasks.put(task.getId(), task);
        return task;
    }

    @Override
    public Optional<Task> findByIdAndUserId(Long id, Long userId) {
        return Optional.ofNullable(tasks.get(id))
                .filter(task -> task.getUserId().equals(userId));
    }

    @Override
    public List<Task> findAllByUserId(Long userId, TaskFilter filter, int page, int pageSize) {
        return tasks.values().stream()
                .filter(t -> matchesFilter(t, userId, filter))
                .skip((long) page * pageSize)
                .limit(pageSize)
                .toList();
    }

    @Override
    public int countByUserId(Long userId, TaskFilter filter) {
        return (int) tasks.values().stream()
                .filter(t -> matchesFilter(t, userId, filter))
                .count();
    }

    @Override
    public boolean deleteByIdAndUserId(Long id, Long userId) {
        return tasks.values().removeIf(task -> task.getUserId().equals(userId) && task.getId().equals(id));
    }

    @Override
    public int deleteAllByUserId(Long userId) {
        int sizeBefore = tasks.size();
        tasks.values().removeIf(task->userId.equals(task.getUserId()));
        return sizeBefore-tasks.size();
    }

    private boolean matchesFilter(Task t, Long userId, TaskFilter filter) {

        if (!t.getUserId().equals(userId)) return false;

        if (filter.status() != null && !filter.status().equals(t.getStatus())) return false;
        if (filter.priority() != null && !filter.priority().equals(t.getPriority())) return false;
        if (filter.categoryId() != null && !filter.categoryId().equals(t.getCategoryId())) return false;

        return true;
    }

}
