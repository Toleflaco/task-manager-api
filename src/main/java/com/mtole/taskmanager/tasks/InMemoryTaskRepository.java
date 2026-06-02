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
                .filter(task ->task.getUserId().equals(userId));
    }

    @Override
    public List<Task> findAllByUserId(Long userId, TaskFilter filter, int page, int pageSize) {
        return tasks.values().stream()
                .filter(t-> matchesFilter(t,userId,filter))
                .skip((long) page*pageSize)
                .limit(pageSize)
                .toList();
    }

    @Override
    public int countByUserId(Long userId, TaskFilter filter) {
        return (int) tasks.values().stream()
                .filter(t->matchesFilter(t,userId,filter))
                .count();
    }

    @Override
    public boolean deleteByIdAndUserId(Long id, Long userId) {
        return tasks.values().removeIf(task -> task.getUserId().equals(userId)&&task.getId().equals(id));
    }

    private boolean matchesFilter(Task t, Long userId, TaskFilter filter) {

       if (!t.getUserId().equals(userId)) return false;

       if (filter.status()!=null && t.getStatus() != filter.status()) return false;
       if (filter.priority()!=null && t.getPriority() != filter.priority()) return false;
       if (filter.categoryId()!=null && !t.getCategoryId().equals(filter.categoryId())) return false;

       return true;
    }

}
