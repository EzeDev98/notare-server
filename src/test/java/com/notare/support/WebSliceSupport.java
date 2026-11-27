package com.notare.support;

import com.notare.common.ratelimit.RateLimitProperties;
import com.notare.common.ratelimit.RateLimiter;
import com.notare.common.version.ClientVersionProperties;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Test stand-in used by the integration tests. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(WebSliceSupport.Config.class)
public @interface WebSliceSupport {

    @TestConfiguration
    class Config {

        @Bean
        ClientVersionProperties clientVersionProperties() {
            ClientVersionProperties properties = new ClientVersionProperties();
            properties.setMinVersion("0.0.0");
            return properties;
        }

        @Bean
        RateLimitProperties rateLimitProperties() {
            RateLimitProperties properties = new RateLimitProperties();
            properties.setSyncPerMinute(1000);
            return properties;
        }

        @Bean
        RateLimiter rateLimiter() {
            return Mockito.mock(RateLimiter.class);
        }
    }
}
