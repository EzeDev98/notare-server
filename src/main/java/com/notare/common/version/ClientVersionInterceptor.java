package com.notare.common.version;

import com.notare.common.exception.ClientTooOldException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Turns away clients older than the minimum supported version. */
@Component
public class ClientVersionInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Notare-Client-Version";

    private final String minimumText;
    private final ClientVersion minimum;

    public ClientVersionInterceptor(ClientVersionProperties properties) {
        this.minimumText = properties.getMinVersion();
        this.minimum = ClientVersion.parse(minimumText);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String sent = request.getHeader(HEADER);
        if (sent == null || sent.isBlank()) {
            return true;
        }
        ClientVersion version;
        try {
            version = ClientVersion.parse(sent);
        } catch (IllegalArgumentException ex) {
            return true;
        }
        if (version.compareTo(minimum) < 0) {
            throw new ClientTooOldException(minimumText);
        }
        return true;
    }
}
