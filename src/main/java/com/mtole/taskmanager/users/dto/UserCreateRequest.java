package com.mtole.taskmanager.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @Schema(description = "User name", example="Juan Mendoza")
        @NotBlank @Size(min=2, max=50) String name,
        @Schema(description="User email", example="juan.mendoza@mendoza.es")
        @NotBlank @Email String email
) {
}
