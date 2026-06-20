package com.mtole.taskmanager.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ActivityEventRepositoryCustom {

    Page<ActivityEvent> search(Long userId, ActivityEventFilter filter, Pageable pageable);
}
