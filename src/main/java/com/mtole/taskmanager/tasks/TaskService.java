package com.mtole.taskmanager.tasks;

import com.mtole.taskmanager.categories.Category;
import com.mtole.taskmanager.categories.CategoryRepository;
import com.mtole.taskmanager.common.ResourceNotFoundException;
import com.mtole.taskmanager.tasks.dto.TaskCreateRequest;
import com.mtole.taskmanager.tasks.dto.TaskSummaryProjection;
import com.mtole.taskmanager.tasks.dto.TaskUpdateRequest;
import com.mtole.taskmanager.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
public class TaskService {
    private final TaskRepository taskRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final TaskMapper taskMapper;
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);


    public TaskService(TaskRepository taskRepository, CategoryRepository categoryRepository, UserRepository userRepository, TaskMapper taskMapper) {
        this.taskRepository = taskRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.taskMapper = taskMapper;
    }

    @Transactional
    public Task create(TaskCreateRequest request, Long currentUserId) {
        log.info("Creating task with title={}", request.title());
        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findByIdAndUserId(request.categoryId(), currentUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        }
        Task entity = taskMapper.toEntity(request);
        entity.setCategory(category);
        entity.setUser(userRepository.getReferenceById(currentUserId));
        entity.setStatus(TaskStatus.PENDING);

        Task saved = taskRepository.save(entity);
        log.info("Task created with id={}", saved.getId());
        return saved;
    }

    @Transactional
    public Task update(Long id, TaskUpdateRequest request, Long currentUserId) {
        log.info("Updating task with id={}", id);
        Task existing = taskRepository.findByIdAndUserId(id, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Task with id=" + id + " not found"));


        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findByIdAndUserId(request.categoryId(), currentUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category with id=" + request.categoryId() + " not found"));
        }

        taskMapper.updateFromRequest(request, existing);
        existing.setCategory(category);// Explícito permite desasignar con category = null
        Task saved = taskRepository.save(existing);
        log.info("Task updated with id={}", saved.getId());
        return saved;
    }

    @Transactional
    public Task complete(Long id, Long currentUserId) {
        log.info("Completing task with id={}", id);
        Task existing = taskRepository.findByIdAndUserId(id, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Task with id=" + id + " not found"));
        TaskStatus currentStatus = existing.getStatus();
        if (currentStatus != TaskStatus.PENDING && currentStatus != TaskStatus.IN_PROGRESS) {
            throw new InvalidTaskStateException("Cannot complete task with id=" + id + ", current status is " + currentStatus);
        }
        existing.setStatus(TaskStatus.COMPLETED);
        existing.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        Task saved = taskRepository.save(existing);
        log.info("Task completed with id={}", saved.getId());
        return saved;
    }

    @Transactional
    public Task cancel(Long id, Long currentUserId) {
        log.info("Canceling task with id={}", id);
        Task existing = taskRepository.findByIdAndUserId(id, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Task with id=" + id + " not found"));
        TaskStatus currentStatus = existing.getStatus();
        if (currentStatus != TaskStatus.PENDING && currentStatus != TaskStatus.IN_PROGRESS) {
            throw new InvalidTaskStateException(
                    "Cannot cancel task with id=" + id + ", current status is " + currentStatus);
        }
        existing.setStatus(TaskStatus.CANCELLED);
        Task saved = taskRepository.save(existing);
        log.info("Task cancelled with id={}", saved.getId());
        return saved;

    }

    @Transactional(readOnly = true)
    public Page<TaskSummaryProjection> findAll(Long currentUserId, TaskFilter filter, Pageable pageable) {
        Specification<Task> spec = buildSpecification(currentUserId, filter);
        return taskRepository.findAllSummariesBy(spec, pageable);
    }

    private Specification<Task> buildSpecification(Long currentUserId, TaskFilter filter) {
        Specification<Task> spec = TaskSpecifications.byUserId(currentUserId);

        if (filter.status() != null) {
            spec = spec.and(TaskSpecifications.byStatus(filter.status()));
        }
        if (filter.priority() != null) {
            spec = spec.and(TaskSpecifications.byPriority(filter.priority()));
        }
        if (filter.categoryName() != null && !filter.categoryName().isBlank()) {
            spec = spec.and(TaskSpecifications.byCategoryName(filter.categoryName()));
        }
        return spec;
    }

    @Transactional(readOnly = true)
    public Optional<Task> findById(Long id, Long currentUserId) {
        return taskRepository.findByIdAndUserId(id, currentUserId);
    }


    @Transactional
    public boolean deleteById(Long id, Long currentUserId) {
        log.info("Deleting task with id={}", id);
        long deleted = taskRepository.deleteByIdAndUserId(id, currentUserId);
        if (deleted > 0) {
            log.info("Deleted task id={}", id);
            return true;
        } else {
            log.warn("Task with id={} not found or not owned by user={}", id, currentUserId);
            return false;
        }
    }

}
