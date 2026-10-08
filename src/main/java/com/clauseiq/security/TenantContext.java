package com.clauseiq.security;

import com.clauseiq.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves the current tenant from the authenticated JWT principal.
 * Services call this instead of accepting a tenantId from request bodies or query params.
 */
public final class TenantContext {

    private TenantContext() {
    }

    public static AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw ApiException.unauthorized("Authentication required");
    }

    public static Long currentTenantId() {
        return currentUser().tenantId();
    }
}
