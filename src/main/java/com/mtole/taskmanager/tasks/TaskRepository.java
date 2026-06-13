package com.mtole.taskmanager.tasks;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    Optional<Task> findByIdAndUserId(Long id, Long userId);

    Page<Task> findAllByUserId(Long userId, Pageable pageable);

    long deleteByIdAndUserId(Long id, Long userId);

    @Query("""
        SELECT t FROM Task t JOIN t.category c
        WHERE t.user.id = :userId AND c.name = :categoryName
        """)
    Page<Task> findByUserIdAndCategoryName(
            @Param("userId") Long userId,
            @Param("categoryName") String categoryName,
            Pageable pageable);
}