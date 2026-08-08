package com.notare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application-wide feature toggles.
 *
 * <p>auditorAwareRef names the bean that answers "who is doing this". Without
 * it, @EnableJpaAuditing still populates @CreatedDate and @LastModifiedDate —
 * which is why the timestamps have worked since Step 1 — but @CreatedBy and
 * @LastModifiedBy stay null forever, with nothing logged to say why. It's the
 * kind of omission that looks like the annotations are broken.
 *
 * <p>The reference is by BEAN NAME, and Spring's default name for
 * SecurityAuditorAware is its uncapitalised class name. Renaming that class
 * without updating this string compiles cleanly and fails at startup.
 *
 * <p>@EnableScheduling moved here in Step 10. The SchedulingConfig class added
 * in 9F is now redundant and should be DELETED — @EnableAsync was already on
 * this class, so putting its sibling anywhere else was a mistake.
 *
 * <p>While you're here: AsyncConfig (Step 5) also carries @EnableAsync, which
 * has always been redundant with the one below. Harmless — the annotation is
 * idempotent — but worth removing so there's one obvious place these live.
 * AsyncConfig's transcriptionExecutor bean must stay.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "securityAuditorAware")
@EnableAsync
@EnableScheduling
public class AppConfig {
}