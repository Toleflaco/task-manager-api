package com.mtole.taskmanager.tasks.dto;

import com.mtole.taskmanager.tasks.Priority;
import com.mtole.taskmanager.tasks.TaskStatus;

import java.time.OffsetDateTime;

public interface TaskSummaryProjection {
    Long getId();

    String getTitle();

    TaskStatus getStatus();

    Priority getPriority();

    OffsetDateTime getDueDate();

    OffsetDateTime getCreatedAt();

    Long getCategoryId();

    String getCategoryName();

}
