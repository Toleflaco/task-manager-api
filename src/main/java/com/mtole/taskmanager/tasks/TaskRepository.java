package com.mtole.taskmanager.tasks;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    @EntityGraph(attributePaths = {"category"})
    @Override
    Page<Task> findAll(Specification<Task> spec, Pageable pageable);

    Optional<Task> findByIdAndUserId(Long id, Long userId);

    long deleteByIdAndUserId(Long id, Long userId);

}