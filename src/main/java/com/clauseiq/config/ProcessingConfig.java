package com.clauseiq.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Contract processing runs in a small background pool so uploads return immediately.
 * Tests set clauseiq.processing.async=false to process synchronously and assert on the result.
 */
@Configuration
public class ProcessingConfig {

    @Bean
    public TaskExecutor contractProcessingExecutor(ClauseIqProperties properties) {
        if (!properties.processing().async()) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("contract-proc-");
        executor.initialize();
        return executor;
    }
}
