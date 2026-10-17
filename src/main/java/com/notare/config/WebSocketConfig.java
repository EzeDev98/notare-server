package com.notare.config;

import com.notare.feature.collab.socket.CollabHandshakeInterceptor;
import com.notare.feature.collab.socket.CollabWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import com.notare.feature.collab.socket.CollabProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the collaboration socket at {@code <app.title>/ws/collab}, that is
 * {@code /api/v1/ws/collab} (step 15c, D-032).
 *
 * <p>The handshake interceptor redeems the ticket before the upgrade, so there
 * is no unauthenticated socket, and a browser's Origin header is checked against
 * the same origins WebSecurityConfig allows for CORS.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final CollabWebSocketHandler collabHandler;
    private final CollabHandshakeInterceptor collabHandshakeInterceptor;

    @Value("${app.title}")
    private String appTitle;

    /** Step 19c: the same setting WebSecurityConfig uses for REST. */
    @Value("${app.cors.allowed-origin-patterns:https://*.onrender.com,http://localhost:*}")
    private String[] allowedOriginPatterns;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(collabHandler, "/" + appTitle + "/ws/collab")
                .addInterceptors(collabHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }

    /**
     * Tomcat refuses WebSocket messages over 8 KB unless told otherwise, and a
     * document update or snapshot can be larger. Allow exactly the protocol's frame
     * limit; anything bigger is closed by the container with 1009.
     */
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> collabFrameLimit() {
        return factory -> factory.addContextCustomizers(context -> context.addParameter(
                "org.apache.tomcat.websocket.binaryBufferSize",
                String.valueOf(CollabProtocol.MAX_FRAME_BYTES)));
    }
}
