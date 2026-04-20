package com.example.fileapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "claudeExecutor")
    public Executor claudeExecutor(@Value("${app.claude.max-concurrent:2}") int maxConcurrent) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(maxConcurrent);
        executor.setMaxPoolSize(maxConcurrent);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("claude-");
        executor.initialize();
        return executor;
    }
}
