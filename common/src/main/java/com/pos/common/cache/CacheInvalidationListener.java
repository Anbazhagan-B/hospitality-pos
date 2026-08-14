package com.pos.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Applies cache invalidations announced by other services.
 *
 * <p>Each service decides for itself what a change means for its own regions.
 * cook-service assembles a {@code terminalConfig} from menu, tender and terminal
 * data, so a menu item changing anywhere invalidates the whole configuration for
 * that organisation - the writer has no way to know that and should not need to.
 *
 * <p>Every eviction is scoped by {@code organizationId}. Clearing a whole region
 * on one tenant's write would throw away every other tenant's warm cache and
 * cause a stampede against the database at the worst possible moment.
 */
@Slf4j
@Component
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnBean(CacheManager.class)
@ConditionalOnProperty(name = "pos.cache.invalidation.enabled", havingValue = "true")
public class CacheInvalidationListener {

    private final CacheManager cacheManager;
    private final RedisPatternEvictor patternEvictor;
    private final String serviceName;

    /**
     * Regions keyed purely by {@code organizationId}. An invalidation for the
     * tenant removes exactly one entry from each.
     */
    private static final Set<String> ORG_SCOPED_REGIONS = Set.of(
            CacheNames.ACTIVE_MENU_ITEMS,
            CacheNames.ORGANIZATION
    );

    public CacheInvalidationListener(CacheManager cacheManager,
                                     RedisPatternEvictor patternEvictor,
                                     @org.springframework.beans.factory.annotation.Value(
                                             "${spring.application.name:unknown}") String serviceName) {
        this.cacheManager = cacheManager;
        this.patternEvictor = patternEvictor;
        this.serviceName = serviceName;
    }

    @KafkaListener(
            topics = "${pos.cache.invalidation.topic:cache-invalidation}",
            groupId = "${spring.application.name}-cache-invalidation")
    public void onInvalidation(CacheInvalidationEvent event) {
        if (event == null || event.getOrganizationId() == null) {
            log.warn("Ignoring cache invalidation with no organizationId: {}", event);
            return;
        }

        // A service does not need to react to its own announcement; @CacheEvict
        // already removed the entry before this was published.
        if (serviceName.equals(event.getOriginService())) {
            return;
        }

        log.debug("Applying cache invalidation from {}: type={} org={} id={}",
                event.getOriginService(), event.getEntityType(),
                event.getOrganizationId(), event.getEntityId());

        try {
            evictOrgScoped(event.getOrganizationId());
            evictEntity(event);
            evictTerminalConfig(event.getOrganizationId());
        } catch (Exception ex) {
            // Never let a failed eviction kill the consumer. The entry expires
            // on its TTL regardless, so the blast radius is bounded staleness
            // rather than a stuck consumer group.
            log.error("Failed to apply cache invalidation eventId={}", event.getEventId(), ex);
        }
    }

    private void evictOrgScoped(Long organizationId) {
        for (String region : ORG_SCOPED_REGIONS) {
            Cache cache = cacheManager.getCache(region);
            if (cache != null) {
                cache.evict(organizationId);
            }
        }
    }

    private void evictEntity(CacheInvalidationEvent event) {
        if (event.getEntityId() == null) {
            return;
        }
        String region = switch (event.getEntityType()) {
            case MENU_ITEM -> CacheNames.MENU_ITEM;
            case ORGANIZATION -> CacheNames.ORGANIZATION;
            default -> null;
        };
        if (region == null) {
            return;
        }
        Cache cache = cacheManager.getCache(region);
        if (cache != null) {
            cache.evict(event.getEntityId());
        }
    }

    /**
     * Terminal configuration embeds menu, tender and terminal data, so any of
     * those changing makes the assembled document stale. The key is
     * {@code organizationId:terminalId} and the terminal ids are not known here,
     * so this is the one place a broader eviction is justified - still scoped to
     * the one tenant.
     */
    private void evictTerminalConfig(Long organizationId) {
        if (cacheManager.getCache(CacheNames.TERMINAL_CONFIG) == null) {
            return;
        }
        // Keys are "terminalConfig::<org>:<terminal>" and the terminal ids are
        // not carried on the event, so this needs a pattern sweep scoped to the
        // one tenant. Cache.evict() cannot express that - it would delete the
        // literal key "<org>:*" and silently do nothing.
        patternEvictor.evictMatching(CacheNames.TERMINAL_CONFIG, organizationId + ":*");
    }
}
