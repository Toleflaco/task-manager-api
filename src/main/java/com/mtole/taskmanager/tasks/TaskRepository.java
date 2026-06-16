package com.mtole.taskmanager.tasks;

import com.mtole.taskmanager.tasks.dto.TaskSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {



    Optional<Task> findByIdAndUserId(Long id, Long userId);

    long deleteByIdAndUserId(Long id, Long userId);

    default Page<TaskSummaryProjection> findAllSummariesBy(Specification<Task> spec, Pageable pageable) {
        return findBy(spec, query -> query.as(TaskSummaryProjection.class).page(pageable));
    }
}