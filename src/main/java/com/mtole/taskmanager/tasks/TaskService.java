package com.mtole.taskmanager.tasks;

import com.mtole.taskmanager.categories.Category;
import com.mtole.taskmanager.categories.CategoryRepository;
import com.mtole.taskmanager.common.ResourceNotFoundException;
import com.mtole.taskmanager.tasks.dto.TaskCreateRequest;
import com.mtole.taskmanager.tasks.dto.TaskUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TaskService {
    private final TaskRepository taskRepository;
    private final CategoryRepository categoryRepository;
    private final TaskMapper taskMapper;
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);


    public TaskService(TaskRepository taskRepository, CategoryRepository categoryRepository, TaskMapper taskMapper) {
        this.taskRepository = taskRepository;
        this.categoryRepository = categoryRepository;
        this.taskMapper = taskMapper;
    }

    public Task create(TaskCreateRequest request, Long currentUserId) {
        log.info("Creating task with title={}", request.title());
        if (request.categoryId() != null) {
            categoryRepository.findByIdAndUserId(request.categoryId(), currentUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        }
        Task entity = taskMapper.toEntity(request);
        entity.setUserId(currentUserId);
        entity.setStatus(TaskStatus.PENDING);
        Task saved = taskRepository.save(entity);
        log.info("Task created with id={}", saved.getId());
        return saved;
    }

    public Task update(Long id, TaskUpdateRequest request, Long currentUserId) {
        log.info("Updating task with id={}", id);
        Task existing = taskRepository.findByIdAndUserId(id, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Task with id=" + id + " not found"));
        if (request.categoryId() != null) {
            categoryRepository.findByIdAndUserId(request.categoryId(), currentUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category with id=" + request.categoryId() + " not found"));
        }
        taskMapper.updateFromRequest(request, existing);
        Task saved = taskRepository.save(existing);
        log.info("Task updated with id={}", saved.getId());
        return saved;
    }

    public Task complete(Long id, Long currentUserId) {
        log.info("Completing task with id={}", id);
        Task existing = taskRepository.findByIdAndUserId(id, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Task with id=" + id + " not found"));
        TaskStatus currentStatus = existing.getStatus();
        if (currentStatus != TaskStatus.PENDING && currentStatus != TaskStatus.IN_PROGRESS) {
            throw new InvalidTaskStateException("Cannot complete task with id=" + id + ", current status is " + currentStatus);
        }
        existing.setStatus(TaskStatus.COMPLETED);
        existing.setCompletedAt(LocalDateTime.now());
        Task saved = taskRepository.save(existing);
        log.info("Task completed with id={}", saved.getId());
        return saved;
    }

    public Task cancel(Long id, Long currentUserId) {
        log.info("Canceling task with id={}", id);
        Task existing = taskRepository.findByIdAndUserId(id, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Task with id=" + id + " not found"));
        TaskStatus currentStatus = existing.getStatus();
        if (currentStatus != TaskStatus.PENDING && currentStatus != TaskStatus.IN_PROGRESS) {
            throw new InvalidTaskStateException("Cannot cancel task with id=" + id + ", current status is " + currentStatus);
        }
        existing.setStatus(TaskStatus.CANCELLED);
        Task saved = taskRepository.save(existing);
        log.info("Task cancelled with id={}", saved.getId());
        return saved;

    }

    public List<Task> findAll(Long currentUserId, TaskFilter filter, int page, int pageSize) {
        return taskRepository.findAllByUserId(currentUserId, filter, page, pageSize);
    }

    public Optional<Task> findById(Long id, Long currentUserId) {
        return taskRepository.findByIdAndUserId(id, currentUserId);
    }

    public int countAll(Long currentUserId, TaskFilter filter) {
        return taskRepository.countByUserId(currentUserId, filter);
    }

    public boolean deleteById(Long id, Long currentUserId) {
        log.info("Deleting task with id={}", id);
        boolean deleted = taskRepository.deleteByIdAndUserId(id, currentUserId);
        if (deleted) {
            log.info("Deleted task id={}", id);
        } else {
            log.warn("Task with id={} not found or not owned by user={}", id, currentUserId);
        }
        return deleted;
    }

}
