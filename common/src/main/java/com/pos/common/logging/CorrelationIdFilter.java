package com.pos.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Establishes the per-request MDC context that makes the ELK pipeline useful.
 *
 * <p>Without a correlation id, tracing one check across check-service,
 * kitchen-display-service and payment-gateway-service means correlating
 * timestamps by hand across three log streams. With it, a single Kibana query
 * — {@code correlationId: "..."} — returns the whole causal chain in order.
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} so the id exists before the
 * security filter chain, request logging or any application code executes. That
 * ordering also means the authenticated user is <em>not</em> yet known here;
 * {@code JwtAuthenticationFilter} enriches the MDC with user identity once the
 * token is validated, and this filter clears everything on the way out.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /**
     * Accepts an inbound correlation id only if it looks like one we issued.
     * An unvalidated header would let a caller inject arbitrary text into every
     * downstream log line — log forging, and a JSON-injection risk on the way
     * into Elasticsearch.
     */
    private static final int MAX_CORRELATION_ID_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        try {
            String correlationId = resolveCorrelationId(request);

            MDC.put(LoggingConstants.CORRELATION_ID, correlationId);
            MDC.put(LoggingConstants.HTTP_METHOD, request.getMethod());
            MDC.put(LoggingConstants.HTTP_PATH, request.getRequestURI());

            // Echo it back so a terminal or the admin UI can quote the id in a
            // support ticket, and so a caller can stitch its own logs to ours.
            response.setHeader(LoggingConstants.CORRELATION_ID_HEADER, correlationId);

            filterChain.doFilter(request, response);

            MDC.put(LoggingConstants.HTTP_STATUS, String.valueOf(response.getStatus()));
            MDC.put(LoggingConstants.DURATION_MS,
                    String.valueOf(System.currentTimeMillis() - startedAt));
        } finally {
            // Servlet containers pool threads. Without this, MDC state leaks
            // into the next request served by the same thread and attributes
            // one customer's log lines to another's correlation id.
            MDC.clear();
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String incoming = request.getHeader(LoggingConstants.CORRELATION_ID_HEADER);
        if (isAcceptable(incoming)) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }

    private boolean isAcceptable(String value) {
        if (!StringUtils.hasText(value) || value.length() > MAX_CORRELATION_ID_LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = Character.isLetterOrDigit(c) || c == '-' || c == '_';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    /**
     * Actuator endpoints are scraped every few seconds by Prometheus and the
     * kubelet's three probes. Correlating those adds nothing and would dominate
     * the index.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator");
    }
}
