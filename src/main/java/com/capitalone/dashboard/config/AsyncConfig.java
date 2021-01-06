package com.capitalone.dashboard.config;

import com.capitalone.dashboard.settings.WhiteSourceSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean("WSCollectorExecutor")
    public Executor taskExecutor(WhiteSourceSettings settings) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(settings.getThreadPoolSettings().getCorePoolSize());
        executor.setMaxPoolSize(settings.getThreadPoolSettings().getMaxPoolSize());
        executor.setQueueCapacity(settings.getThreadPoolSettings().getQueueCapacity());
        executor.setThreadNamePrefix(settings.getThreadPoolSettings().getThreadNamePrefix());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setBeanName("WSCollectorExecutor");
        executor.initialize();
        return executor;
    }
}
