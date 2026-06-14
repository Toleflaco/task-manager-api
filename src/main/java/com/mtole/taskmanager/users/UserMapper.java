package com.mtole.taskmanager.users;

import com.mtole.taskmanager.users.dto.UserCreateRequest;
import com.mtole.taskmanager.users.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel="spring",
unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target ="password", ignore = true)
    User toEntity(UserCreateRequest request);
    UserResponse toResponse(User user);
}
