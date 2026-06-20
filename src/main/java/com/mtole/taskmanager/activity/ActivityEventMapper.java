package com.mtole.taskmanager.activity;

import com.mtole.taskmanager.activity.dto.ActivityEventResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ActivityEventMapper {

    ActivityEventResponse toResponse(ActivityEvent entity);
}
