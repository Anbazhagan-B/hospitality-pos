package com.pos.common.logging;

/**
 * MDC keys and header names shared by every service.
 *
 * <p>Each MDC key listed here is whitelisted in {@code logback-spring.xml} via
 * {@code includeMdcKeyName}, which means it becomes a first-class field in the
 * JSON log event and therefore a searchable field in Elasticsearch. Adding a key
 * here without adding it to the logback config will silently drop it.
 */
public final class LoggingConstants {

    private LoggingConstants() {
    }

    /** Correlation id that follows one logical operation across every service. */
    public static final String CORRELATION_ID = "correlationId";

    /** Authenticated employee id, populated once the JWT filter has run. */
    public static final String USER_ID = "userId";

    /** Authenticated username, populated once the JWT filter has run. */
    public static final String USERNAME = "username";

    /** Tenant discriminator. Every POS query is scoped by this. */
    public static final String ORGANIZATION_ID = "organizationId";

    /** Originating POS terminal, where the caller supplies it. */
    public static final String TERMINAL_ID = "terminalId";

    public static final String HTTP_METHOD = "httpMethod";
    public static final String HTTP_PATH = "path";
    public static final String HTTP_STATUS = "httpStatus";
    public static final String DURATION_MS = "durationMs";

    /** Kafka topic a consumer thread is currently processing. */
    public static final String KAFKA_TOPIC = "kafkaTopic";

    /** Inbound/outbound HTTP header carrying {@link #CORRELATION_ID}. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /** Kafka record header carrying {@link #CORRELATION_ID}. */
    public static final String CORRELATION_ID_KAFKA_HEADER = "X-Correlation-Id";
}
