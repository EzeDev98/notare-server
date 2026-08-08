package com.notare.feature.transcription.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * The dedicated, bounded thread pool for transcription work. The worker in 5C
 * uses @Async("transcriptionExecutor") to run on this pool specifically.
 *
 * <h2>What this class no longer does</h2>
 *
 * <p>It carried @EnableAsync, duplicating the one on AppConfig. Removed: this
 * class configures a pool, and enabling a framework feature application-wide is
 * a different concern that belongs in one obvious place.
 *
 * <p>The bean below must stay, and is the entire reason the class exists. It is
 * bulkhead isolation: transcription saturates its own four threads rather than
 * Spring's shared default executor, so a queue of transcription jobs cannot
 * starve request handling. The bounded queue is backpressure — when 50 jobs are
 * waiting, the 51st is rejected loudly instead of quietly allocating threads
 * until the JVM runs out.
 *
 * <p>Apply the general test to this file: what does it do that Boot would not?
 * It defines a named executor with deliberate limits. That is a real answer.
 * @EnableAsync was not.
 */
@Configuration
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