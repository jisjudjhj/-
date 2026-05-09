package com.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncExecutorConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor(
            @Value("${async.executors.default.core-pool-size:4}") int corePoolSize,
            @Value("${async.executors.default.max-pool-size:12}") int maxPoolSize,
            @Value("${async.executors.default.queue-capacity:500}") int queueCapacity,
            @Value("${async.executors.default.keep-alive-seconds:60}") int keepAliveSeconds) {
        return buildExecutor("default-async-", corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds);
    }

    @Bean(name = "orderTaskExecutor")
    public Executor orderTaskExecutor(
            @Value("${async.executors.order.core-pool-size:8}") int corePoolSize,
            @Value("${async.executors.order.max-pool-size:20}") int maxPoolSize,
            @Value("${async.executors.order.queue-capacity:1000}") int queueCapacity,
            @Value("${async.executors.order.keep-alive-seconds:60}") int keepAliveSeconds) {
        return buildExecutor("order-async-", corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds);
    }

    @Bean(name = "supportTaskExecutor")
    public Executor supportTaskExecutor(
            @Value("${async.executors.support.core-pool-size:6}") int corePoolSize,
            @Value("${async.executors.support.max-pool-size:16}") int maxPoolSize,
            @Value("${async.executors.support.queue-capacity:800}") int queueCapacity,
            @Value("${async.executors.support.keep-alive-seconds:60}") int keepAliveSeconds) {
        return buildExecutor("support-async-", corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds);
    }

    @Bean(name = "recommendationTaskExecutor")
    public Executor recommendationTaskExecutor(
            @Value("${async.executors.recommendation.core-pool-size:4}") int corePoolSize,
            @Value("${async.executors.recommendation.max-pool-size:12}") int maxPoolSize,
            @Value("${async.executors.recommendation.queue-capacity:1200}") int queueCapacity,
            @Value("${async.executors.recommendation.keep-alive-seconds:60}") int keepAliveSeconds) {
        return buildExecutor("recommend-async-", corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds);
    }

    private Executor buildExecutor(String prefix, int corePoolSize, int maxPoolSize, int queueCapacity, int keepAliveSeconds) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefix);
        executor.setCorePoolSize(Math.max(1, corePoolSize));
        executor.setMaxPoolSize(Math.max(corePoolSize, maxPoolSize));
        executor.setQueueCapacity(Math.max(100, queueCapacity));
        executor.setKeepAliveSeconds(Math.max(30, keepAliveSeconds));
        executor.setAllowCoreThreadTimeOut(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
