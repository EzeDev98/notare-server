package com.notare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application-wide feature toggles. One place, on purpose.
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
 * <h2>Why both @EnableAsync and @EnableScheduling live here</h2>
 *
 * <p>They used to be spread across three classes: this one, a SchedulingConfig
 * that did nothing else, and AsyncConfig in the transcription module, which
 * repeated @EnableAsync alongside the bean it actually exists for.
 *
 * <p>The duplication was harmless — these annotations are idempotent, so
 * nothing misbehaved — but that is exactly what made it worth removing.
 * A duplicated enable-switch costs nothing today and misleads tomorrow:
 * someone deleting AsyncConfig along with the transcription feature would have
 * had no way to know whether @Async elsewhere in the application still worked,
 * because the answer depended on a second declaration in a file they were not
 * reading.
 *
 * <p>A single home makes the question answerable by looking in one place, and
 * makes "what does this application enable?" a file you can open rather than a
 * grep you have to remember to run.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "securityAuditorAware")
@EnableAsync
@EnableScheduling
public class AppConfig {
}