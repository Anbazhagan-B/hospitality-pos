package com.pos.common.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Redis-backed cache for read-mostly reference data.
 *
 * <p>Inert unless the service both depends on spring-data-redis and sets
 * {@code pos.cache.enabled=true}, so the four services that have no business
 * caching anything - check, payment, kitchen, admin - are unaffected.
 *
 * <p>Deliberately applied at the <em>service</em> layer rather than as a
 * Hibernate second-level cache. An entry here holds a finished DTO, so a hit
 * skips the query, the entity hydration and the mapping. An L2 hit would skip
 * only the query, and the Hibernate query cache invalidates an entire region on
 * any write to the underlying table - which on a database shared by seven
 * services means a hit rate close to zero.
 */
@Slf4j
@Configuration
@EnableCaching
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnProperty(name = "pos.cache.enabled", havingValue = "true")
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(CacheNames.DEFAULT_TTL)
                // A null result is not cached. Caching absence here would mean a
                // menu item created a second after a failed lookup stays
                // invisible for the whole TTL.
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(cacheObjectMapper())));

        Map<String, RedisCacheConfiguration> perRegion = new HashMap<>();
        CacheNames.TTLS.forEach((name, ttl) -> perRegion.put(name, base.entryTtl(ttl)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                // This both pre-creates the regions and gives each its own TTL.
                //
                // Do NOT add initialCacheNames() alongside this: that method
                // maps every name it is given to the *default* configuration and
                // merges it over this map, silently replacing the per-region
                // TTLs above with the 5-minute default. The regions still work,
                // so nothing fails - the TTLs are just quietly wrong.
                .withInitialCacheConfigurations(perRegion)
                .transactionAware()
                .build();
    }

    /**
     * JSON rather than JDK serialization: entries stay readable with
     * {@code redis-cli}, survive unrelated class changes, and are not tied to
     * one JVM's serialVersionUID.
     */
    private ObjectMapper cacheObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // Type information is required to deserialize back into the
                // concrete DTO. The validator is an allowlist rather than
                // LaissezFaireSubTypeValidator: anything that can write to Redis
                // could otherwise name an arbitrary class and turn a cache read
                // into a deserialization gadget.
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder()
                                .allowIfSubType("com.pos.")
                                .allowIfSubType("java.util.")
                                .allowIfSubType("java.time.")
                                .allowIfSubType("java.math.")
                                .allowIfSubType("java.lang.")
                                .build(),
                        ObjectMapper.DefaultTyping.NON_FINAL,
                        JsonTypeInfo.As.PROPERTY)
                .build();
    }

    /**
     * Fail open when Redis is unavailable.
     *
     * <p>The default handler rethrows, which would turn a Redis outage into a
     * 500 on every cached read path - the cache becomes a hard dependency and a
     * single point of failure for a system that was working fine before it was
     * added. For a POS that trade is unacceptable: losing the cache should mean
     * slower reads against Postgres, not a restaurant that cannot take orders.
     *
     * <p>Put failures are swallowed for the same reason. Evict failures are
     * logged at WARN because they are the dangerous case - a failed eviction
     * leaves stale data behind until the TTL expires.
     */
    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new SimpleCacheErrorHandler() {

            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache read failed, falling through to source. cache={} key={} error={}",
                        cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("Cache write failed, result not cached. cache={} key={} error={}",
                        cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache eviction FAILED - stale data may be served until TTL. "
                        + "cache={} key={} error={}", cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.warn("Cache clear FAILED - stale data may be served until TTL. cache={} error={}",
                        cache.getName(), ex.getMessage());
            }
        };
    }
}
