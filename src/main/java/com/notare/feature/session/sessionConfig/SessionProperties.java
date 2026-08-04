package com.notare.feature.session.sessionConfig;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.session")
public class SessionProperties {

    /** How long a refresh token / session stays valid without being used. */
    private int refreshTokenExpiryDays = 60;

    /** Max concurrent active sessions per user; oldest is evicted beyond this. */
    private int maxConcurrentSessions = 5;
}