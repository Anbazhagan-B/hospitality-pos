package com.pos.common.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Broadcast when cached reference data changes, so services other than the
 * writer can drop their own derived copies.
 *
 * <p>Why this exists when the cache is already shared Redis: {@code @CacheEvict}
 * on the writing service removes the entry from the region <em>that service
 * owns</em>. It cannot know that cook-service holds an assembled
 * {@code terminalConfig} built from the same menu, in a different region, under
 * a different key. Only the owner of that region knows how to invalidate it, so
 * the writer announces the change and each service decides what it means.
 *
 * <p>Consumers must treat this as advisory and idempotent. Evicting an entry
 * that is already gone is a no-op, duplicate deliveries are harmless, and a lost
 * event degrades to the region's TTL rather than to permanent staleness.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheInvalidationEvent {

    public enum EntityType {
        MENU_ITEM,
        MENU_CATEGORY,
        ORGANIZATION,
        PROFIT_CENTER,
        TENDER,
        TERMINAL
    }

    private String eventId;

    /** What changed. Consumers map this onto their own regions. */
    private EntityType entityType;

    /** Tenant the change belongs to. Always populated - it scopes every eviction. */
    private Long organizationId;

    /**
     * Primary key of the changed record, or {@code null} to mean "everything of
     * this type for this organisation is now suspect".
     */
    private Long entityId;

    /** Service that published the change. Useful when tracing a stale read. */
    private String originService;

    private LocalDateTime timestamp;
}
