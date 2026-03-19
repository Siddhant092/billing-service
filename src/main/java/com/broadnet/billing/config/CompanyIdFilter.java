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
 * Extracts companyId, userId, and userRole from headers forwarded by the API gateway.
 *
 * Architecture Plan: auth service validates JWT → API gateway sets headers:
 *   X-Company-Id  → companyId (Long)
 *   X-User-Id     → userId (Long)
 *   X-User-Role   → "ROLE_USER" or "ROLE_ADMIN"
 *
 * This billing service NEVER validates JWTs — that is entirely the auth service's job.
 * Controllers read these as @RequestAttribute("companyId") etc.
 *
 * Excluded paths (no company context needed):
 *   /webhooks/**   — Stripe webhooks (verified by Stripe signature)
 *   /actuator/**   — health checks
 */
@Slf4j
@Component
public class CompanyIdFilter extends OncePerRequestFilter {

    public static final String ATTR_COMPANY_ID = "companyId";
    public static final String ATTR_USER_ID    = "userId";
    public static final String ATTR_USER_ROLE  = "userRole";

    private static final String HEADER_COMPANY_ID = "X-Company-Id";
    private static final String HEADER_USER_ID    = "X-User-Id";
    private static final String HEADER_USER_ROLE  = "X-User-Role";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/webhooks/") || path.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String companyHeader = request.getHeader(HEADER_COMPANY_ID);
        String userHeader    = request.getHeader(HEADER_USER_ID);
        String roleHeader    = request.getHeader(HEADER_USER_ROLE);

        // companyId is mandatory for all /api/** paths
        if (companyHeader == null || companyHeader.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":400,\"errorCode\":\"MISSING_COMPANY_ID\"," +
                            "\"message\":\"X-Company-Id header is required\"}");
            return;
        }

        try {
            request.setAttribute(ATTR_COMPANY_ID, Long.parseLong(companyHeader.trim()));
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":400,\"errorCode\":\"INVALID_COMPANY_ID\"," +
                            "\"message\":\"X-Company-Id must be a valid numeric ID\"}");
            return;
        }

        if (userHeader != null && !userHeader.isBlank()) {
            try {
                request.setAttribute(ATTR_USER_ID, Long.parseLong(userHeader.trim()));
            } catch (NumberFormatException e) {
                log.warn("Invalid X-User-Id header value: {}", userHeader);
            }
        }

        if (roleHeader != null && !roleHeader.isBlank()) {
            request.setAttribute(ATTR_USER_ROLE, roleHeader.trim());
        }

        chain.doFilter(request, response);
    }
}