package com.notare.common.ratelimit;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Per-user request limits (step 17j, B-15). The values live in application.properties
 * (notare.rate-limit.*) and nowhere else; the application refuses to start if one is missing.
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "notare.rate-limit")
public class RateLimitProperties {

    /** GET /sync per user per minute. */
    @Positive
    private int syncPerMinute;

    /** GET /user/lookup and GET /user/search together, per user per minute. */
    @Positive
    private int lookupPerMinute;
}
