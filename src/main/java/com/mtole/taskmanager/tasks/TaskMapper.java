package com.mtole.taskmanager.tasks;

import com.mtole.taskmanager.tasks.dto.TaskCreateRequest;
import com.mtole.taskmanager.tasks.dto.TaskResponse;
import com.mtole.taskmanager.tasks.dto.TaskUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;


@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TaskMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    Task toEntity(TaskCreateRequest req);

    TaskResponse toResponse(Task task);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    void updateFromRequest(TaskUpdateRequest req, @MappingTarget Task task);

}

