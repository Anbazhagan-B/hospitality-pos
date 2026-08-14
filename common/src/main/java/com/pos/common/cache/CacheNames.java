package com.pos.common.cache;

import java.time.Duration;
import java.util.Map;

/**
 * Cache region names and their time-to-live, in one place.
 *
 * <p>Every region declared here gets an explicit TTL. A cache without a TTL is a
 * memory leak with good intentions: entries for deleted menu items, closed
 * organisations and decommissioned terminals accumulate forever, and Redis
 * eventually evicts something you actually needed under memory pressure.
 *
 * <p>TTL is the backstop, not the primary invalidation mechanism. Writes evict
 * eagerly, both locally and - for cross-service regions - over Kafka. TTL only
 * bounds how long a missed invalidation can go unnoticed.
 */
public final class CacheNames {

    private CacheNames() {
    }

    /** Single menu item keyed by its global primary key. */
    public static final String MENU_ITEM = "menuItem";

    /** All active menu items for one organisation, keyed by organizationId. */
    public static final String ACTIVE_MENU_ITEMS = "activeMenuItems";

    /** Organisation record keyed by its global primary key. */
    public static final String ORGANIZATION = "organization";

    /** Assembled terminal configuration, keyed by organizationId:terminalId. */
    public static final String TERMINAL_CONFIG = "terminalConfig";

    /**
     * Reference data changes rarely but is read on every order, so it tolerates
     * a long TTL. Terminal config is an aggregate built from several sources and
     * is invalidated by event, so its TTL is only a safety net.
     */
    public static final Map<String, Duration> TTLS = Map.of(
            MENU_ITEM, Duration.ofMinutes(30),
            ACTIVE_MENU_ITEMS, Duration.ofMinutes(15),
            ORGANIZATION, Duration.ofHours(1),
            TERMINAL_CONFIG, Duration.ofMinutes(10)
    );

    /** Fallback for any region not listed above. */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
}
