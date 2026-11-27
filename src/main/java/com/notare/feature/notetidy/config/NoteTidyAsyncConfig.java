package com.notare.feature.notetidy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * The pool AI tidy runs on, kept apart from transcription so a backlog of
 * tidy work never delays a transcript.
 */
@Configuration
public class NoteTidyAsyncConfig {

    @Bean(name = "noteTidyExecutor")
    public Executor noteTidyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("tidy-");
        // On shutdown, let a note in progress finish its save instead of cutting it off.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
