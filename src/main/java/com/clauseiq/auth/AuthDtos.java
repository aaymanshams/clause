package com.clauseiq.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request/response bodies for auth and user management. Password hashes never leave the service layer. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterOrganizationRequest(
            @NotBlank @Size(max = 200) String organizationName,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull Role role) {
    }

    public record AuthResponse(String token, long expiresInSeconds, UserResponse user) {
    }

    public record UserResponse(Long id, Long tenantId, String tenantName, String email, Role role) {
    }
}
