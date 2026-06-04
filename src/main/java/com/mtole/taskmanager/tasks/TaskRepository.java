package com.mtole.taskmanager.tasks;


import java.util.List;
import java.util.Optional;

public interface TaskRepository {
    Task save(Task task);
    Optional<Task> findByIdAndUserId(Long id, Long userId);
    List<Task> findAllByUserId(Long userId, TaskFilter filter, int page, int pageSize);
    int countByUserId(Long userId, TaskFilter filter);
    boolean deleteByIdAndUserId(Long id, Long userId);
    int deleteAllByUserId(Long userId);

}
