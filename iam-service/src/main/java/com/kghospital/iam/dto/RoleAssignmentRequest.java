package com.kghospital.iam.dto;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
public record RoleAssignmentRequest(@NotBlank String username, List<String> roles) {}
