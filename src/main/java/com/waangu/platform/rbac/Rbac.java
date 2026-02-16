package com.waangu.platform.rbac;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for role-based access control (RBAC) enforcement.
 * <p>
 * Provides helper methods to check user roles from the Spring Security context.
 * Roles are expected to follow the "ROLE_" prefix convention.
 * </p>
 */
public final class Rbac {

    public static void requireAnyRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No auth");
        Set<String> have = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        for (String r : roles) {
            if (have.contains("ROLE_" + r)) return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing required role");
    }

    private Rbac() {}
}
