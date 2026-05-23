package com.example.aiengineservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.util.concurrent.Executor;

@Configuration
public class AiEngineConfig {

    @Bean
    public Clock aiEngineClock() {
        return Clock.systemUTC();
    }

    @Bean(name = "aiInvestigationExecutor")
    public Executor aiInvestigationExecutor(
            @Value("${app.ai-investigation.concurrency:4}") int concurrency,
            @Value("${app.ai-investigation.queue-capacity:100}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ai-investigation-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
