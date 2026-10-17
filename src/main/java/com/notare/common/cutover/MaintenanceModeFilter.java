package com.notare.common.cutover;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Step 18g: maintenance mode for the data migration. While MAINTENANCE_MODE=true every request gets
 * 503 with Retry-After, except the admin endpoints (the migration runs through them) and health checks.
 * Off by default. Runs after the security chain (order -100), so the CORS headers are already on the answer.
 */
@Component
@Order(-50)
public class MaintenanceModeFilter extends OncePerRequestFilter {

    @Value("${app.maintenance.enabled:false}")
    private boolean enabled;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !enabled || path.contains("/admin/") || path.contains("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setHeader("Retry-After", "300");
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Service Unavailable\",\"status\":503,"
                + "\"code\":\"MAINTENANCE\",\"detail\":\"Notare is being updated. Try again in a few minutes.\"}");
    }
}
