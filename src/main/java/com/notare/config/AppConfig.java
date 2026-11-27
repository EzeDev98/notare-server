package com.notare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on JPA auditing, async work and scheduling for the whole application. */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "securityAuditorAware")
@EnableAsync
@EnableScheduling
public class AppConfig {
}
