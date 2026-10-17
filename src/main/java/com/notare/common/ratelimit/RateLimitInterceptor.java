package com.notare.common.ratelimit;

import com.notare.feature.user.model.UserDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.function.IntSupplier;

/**
 * Applies one per-user limit to the GET requests of the paths it is registered for (WebMvcConfig).
 * It runs after the security filters, so the caller is already known; anyone not signed in is
 * left to the 401 and never counted. Each bucket is a separate counter per user.
 */
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final String bucket;
    private final IntSupplier limitPerMinute;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetail userDetail) {
            rateLimiter.check(bucket, userDetail.getUser().getId(), limitPerMinute.getAsInt());
        }
        return true;
    }
}
