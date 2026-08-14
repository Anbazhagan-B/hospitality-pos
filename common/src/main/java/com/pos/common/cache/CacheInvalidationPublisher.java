package com.pos.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Announces reference-data changes so other services can drop derived caches.
 *
 * <p>Publishing is best-effort and never blocks or fails the write that
 * triggered it. A menu price update must succeed even when Kafka is unreachable;
 * the worst case is another service serving the old price until its TTL
 * expires, which beats refusing to let an administrator change a price.
 *
 * <p>{@code @Async} is load-bearing, not decoration. {@code KafkaTemplate.send()}
 * blocks for {@code max.block.ms} resolving topic metadata before it returns its
 * future, so calling it inline on a dead broker parks the request thread for a
 * minute. See {@link CacheInvalidationAsyncConfig}.
 */
@Slf4j
@Component
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(name = "pos.cache.invalidation.enabled", havingValue = "true")
public class CacheInvalidationPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String serviceName;

    @Value("${pos.cache.invalidation.topic:cache-invalidation}")
    private String topic;

    public CacheInvalidationPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                      @Value("${spring.application.name:unknown}") String serviceName) {
        this.kafkaTemplate = kafkaTemplate;
        this.serviceName = serviceName;
    }

    @org.springframework.scheduling.annotation.Async(CacheInvalidationAsyncConfig.EXECUTOR)
    public void publish(CacheInvalidationEvent.EntityType entityType,
                        Long organizationId,
                        Long entityId) {
        CacheInvalidationEvent event = CacheInvalidationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .entityType(entityType)
                .organizationId(organizationId)
                .entityId(entityId)
                .originService(serviceName)
                .timestamp(LocalDateTime.now())
                .build();

        try {
            // Keyed by organisation so all invalidations for one tenant land on
            // the same partition and are therefore consumed in order. Without
            // that, a delete and a subsequent re-create could be applied in
            // reverse and leave the cache holding a deleted record.
            kafkaTemplate.send(topic, String.valueOf(organizationId), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Cache invalidation not published - other services may serve "
                                            + "stale data until TTL. type={} org={} id={}",
                                    entityType, organizationId, entityId, ex);
                        } else {
                            log.debug("Published cache invalidation type={} org={} id={}",
                                    entityType, organizationId, entityId);
                        }
                    });
        } catch (Exception ex) {
            log.error("Failed to publish cache invalidation type={} org={} id={}",
                    entityType, organizationId, entityId, ex);
        }
    }
}
