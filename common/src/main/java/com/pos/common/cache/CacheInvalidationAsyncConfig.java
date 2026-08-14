package com.pos.common.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Executor for cache-invalidation publishing.
 *
 * <p>Exists because {@code KafkaTemplate.send()} is not as asynchronous as it
 * looks. It returns a {@code CompletableFuture}, but before it can do so the
 * producer must resolve topic metadata, and when no broker is reachable that
 * call blocks for {@code max.block.ms} - 60 seconds by default. On the request
 * thread that means an administrator updating a menu price waits a full minute
 * and then sees a timeout, purely because a cache-invalidation notice could not
 * be sent. The write itself had already succeeded.
 *
 * <p>Two defences, applied together: publishing runs on this executor rather
 * than the request thread, and {@code max.block.ms} is lowered in each service's
 * Kafka producer configuration so the worker itself cannot be parked for a
 * minute either.
 */
@Configuration
@EnableAsync
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(name = "pos.cache.invalidation.enabled", havingValue = "true")
public class CacheInvalidationAsyncConfig {

    public static final String EXECUTOR = "cacheInvalidationExecutor";

    @Bean(EXECUTOR)
    public Executor cacheInvalidationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        // Bounded. An unbounded queue would hide a broker outage by absorbing
        // work until the heap runs out; a bounded one surfaces it immediately
        // through the rejection policy below.
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("cache-invalidation-");
        // Discard oldest rather than CallerRuns: CallerRuns would push the work
        // back onto the request thread, reinstating exactly the stall this class
        // exists to prevent. A dropped invalidation degrades to the region TTL.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
