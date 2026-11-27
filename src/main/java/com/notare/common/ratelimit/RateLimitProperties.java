package com.notare.common.ratelimit;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Per-user request limits. */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "notare.rate-limit")
public class RateLimitProperties {

    @Positive
    private int syncPerMinute;
}
