package com.pos.common.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deletes cache entries matching a key pattern.
 *
 * <p>Needed because {@link org.springframework.cache.Cache#evict(Object)} takes
 * a single key. Passing it {@code "3:*"} does not wildcard - it deletes the
 * literal key {@code region::3:*}, which never exists, so the eviction silently
 * does nothing and stale data is served until TTL.
 *
 * <p>Some regions are keyed by a composite the invalidating service cannot fully
 * reconstruct. {@code terminalConfig} is keyed {@code organizationId:terminalId},
 * and a menu change invalidates every terminal in that organisation without
 * anyone knowing the terminal ids. Clearing the whole region instead would
 * discard every other tenant's warm entries and cause a database stampede.
 */
@Slf4j
@Component
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnBean(StringRedisTemplate.class)
@RequiredArgsConstructor
public class RedisPatternEvictor {

    private final StringRedisTemplate redisTemplate;

    /** Batch size for deletes; keeps one round-trip from growing unbounded. */
    private static final int DELETE_BATCH = 500;

    /** Upper bound on a single sweep, so a bad pattern cannot run away. */
    private static final int MAX_KEYS = 10_000;

    /**
     * @param cacheName  Spring cache region, e.g. {@code terminalConfig}
     * @param keyPattern glob applied to the key within the region, e.g. {@code 3:*}
     * @return number of keys deleted
     */
    public long evictMatching(String cacheName, String keyPattern) {
        // RedisCacheManager writes keys as "<region>::<key>" with a plain string
        // serializer, so the pattern can be matched directly.
        String pattern = cacheName + "::" + keyPattern;

        List<String> batch = new ArrayList<>(DELETE_BATCH);
        long deleted = 0;
        int seen = 0;

        // SCAN rather than KEYS: KEYS walks the entire keyspace in one blocking
        // call and will stall every other Redis client while it runs.
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                seen++;

                if (batch.size() >= DELETE_BATCH) {
                    deleted += deleteBatch(batch);
                }
                if (seen >= MAX_KEYS) {
                    log.warn("Pattern eviction hit the {} key ceiling for '{}'; "
                            + "remaining entries will expire on TTL", MAX_KEYS, pattern);
                    break;
                }
            }
            if (!batch.isEmpty()) {
                deleted += deleteBatch(batch);
            }
        } catch (Exception ex) {
            // Same fail-open posture as the cache itself: a failed eviction
            // means bounded staleness, not a failed request.
            log.error("Pattern eviction failed for '{}' - entries will expire on TTL", pattern, ex);
            return deleted;
        }

        log.debug("Pattern eviction removed {} entries matching '{}'", deleted, pattern);
        return deleted;
    }

    private long deleteBatch(List<String> keys) {
        Long removed = redisTemplate.delete(keys);
        keys.clear();
        return removed == null ? 0 : removed;
    }
}
