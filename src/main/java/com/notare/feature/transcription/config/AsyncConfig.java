package com.notare.feature.transcription.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Turns on Spring's @Async support and defines a DEDICATED, bounded thread pool
 * for transcription work. The worker in 5C uses @Async("transcriptionExecutor")
 * to run on this pool specifically.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "transcriptionExecutor")
    public Executor transcriptionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);    // threads kept alive even when idle
        executor.setMaxPoolSize(4);     // hard ceiling on concurrent jobs
        executor.setQueueCapacity(50);  // jobs wait here when all threads are busy
        executor.setThreadNamePrefix("transcribe-");
        // No explicit initialize(): Spring calls it via InitializingBean when it
        // creates the bean.
        return executor;
    }
}