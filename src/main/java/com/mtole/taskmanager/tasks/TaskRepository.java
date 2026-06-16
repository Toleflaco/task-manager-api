package com.mtole.taskmanager.tasks;

import com.mtole.taskmanager.tasks.dto.TaskStatsResponse;
import com.mtole.taskmanager.tasks.dto.TaskSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {



    Optional<Task> findByIdAndUserId(Long id, Long userId);

    long deleteByIdAndUserId(Long id, Long userId);

    default Page<TaskSummaryProjection> findAllSummariesBy(Specification<Task> spec, Pageable pageable) {
        return findBy(spec, query -> query.as(TaskSummaryProjection.class).page(pageable));
    }

    @Query("""
                SELECT new com.mtole.taskmanager.tasks.dto.TaskStatsResponse(
                COUNT(t),
                COALESCE(SUM(CASE WHEN t.status = com.mtole.taskmanager.tasks.TaskStatus.PENDING THEN 1L ELSE 0L END),0L),
                COALESCE(SUM(CASE WHEN t.status = com.mtole.taskmanager.tasks.TaskStatus.IN_PROGRESS THEN 1L ELSE 0L END),0L),
                COALESCE(SUM(CASE WHEN t.status = com.mtole.taskmanager.tasks.TaskStatus.COMPLETED THEN 1L ELSE 0L END),0L),
                COALESCE(SUM(CASE WHEN t.status = com.mtole.taskmanager.tasks.TaskStatus.CANCELLED THEN 1L ELSE 0L END),0L)
                )
                FROM Task t 
                WHERE t.user.id = :userId
            """)
    TaskStatsResponse findStatsByUserId(@Param("userId") Long userId);
}