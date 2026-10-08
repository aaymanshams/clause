package com.clauseiq.security;

import com.clauseiq.auth.Role;

/**
 * Principal stored in the SecurityContext. Built only from a verified JWT, so its tenantId
 * is the single trusted source of "which tenant is this request for".
 */
public record AuthenticatedUser(Long userId, Long tenantId, String email, Role role) {
}
