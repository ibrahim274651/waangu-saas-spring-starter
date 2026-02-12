package com.waangu.platform.rbac;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * RBAC helper for copilot and endpoints (chagpt).
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
