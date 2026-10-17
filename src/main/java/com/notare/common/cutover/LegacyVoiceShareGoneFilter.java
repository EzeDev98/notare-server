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
import java.util.regex.Pattern;

/**
 * Step 18g (B-10): after the migration the old voice-share endpoints answer 410 Gone for one release,
 * then the voiceshare module and its tables are deleted. Covers /shared-voice-notes and
 * /voice-notes/{id}/shares. Off by default (LEGACY_VOICE_SHARE_GONE=true switches it on).
 */
@Component
@Order(-49)
public class LegacyVoiceShareGoneFilter extends OncePerRequestFilter {

    private static final Pattern OWNER_SHARES = Pattern.compile("/voice-notes/\\d+/shares(/.*)?$");

    @Value("${app.legacy.voice-share-gone:false}")
    private boolean enabled;

    @Value("${app.title}")
    private String appTitle;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        String path = request.getRequestURI();
        boolean recipientSide = path.startsWith(appTitle + "/shared-voice-notes");
        return !recipientSide && !OWNER_SHARES.matcher(path).find();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setStatus(HttpServletResponse.SC_GONE);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Gone\",\"status\":410,"
                + "\"code\":\"VOICE_SHARES_REMOVED\",\"detail\":\"Voice clips are now shared with their note.\"}");
    }
}
