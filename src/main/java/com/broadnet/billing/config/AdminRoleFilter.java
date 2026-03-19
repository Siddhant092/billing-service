package com.broadnet.billing.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enforces ROLE_ADMIN on all /api/admin/** endpoints.
 *
 * Architecture Plan: Admin APIs are protected — only users with ROLE_ADMIN
 * (set by the API gateway via X-User-Role header) can access them.
 *
 * This filter runs AFTER CompanyIdFilter so the role attribute is already set.
 * Returns HTTP 403 if role is absent or not ROLE_ADMIN.
 */
@Slf4j
@Component
public class AdminRoleFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String ROLE_ADMIN        = "ROLE_ADMIN";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String role = request.getHeader(HEADER_USER_ROLE);

        if (!ROLE_ADMIN.equals(role)) {
            log.warn("Forbidden: admin endpoint accessed with role={} path={}",
                    role, request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":403,\"errorCode\":\"FORBIDDEN\"," +
                            "\"message\":\"Admin privileges required\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}