package com.pos.common.aspect;

import com.pos.common.logging.LoggingConstants;
import com.pos.common.logging.SensitiveDataMasker;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.argument.StructuredArguments;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Cross-cutting logging for every service, emitted as structured fields rather
 * than interpolated text.
 *
 * <p>{@link StructuredArguments#keyValue} puts each value in its own JSON field
 * <em>and</em> renders it into the human-readable message, so one call site
 * serves both the local console and Elasticsearch. The practical difference is
 * that {@code durationMs} arrives in Kibana as a number you can chart and
 * aggregate, instead of text buried inside a message string you would have to
 * grok-parse.
 *
 * <p>Three deliberate changes from the original aspect:
 * <ul>
 *   <li><b>Arguments and return values log at DEBUG, not INFO.</b> At INFO they
 *       were emitting a full request and response body for every call, which in
 *       Elasticsearch is both an indexing cost and a data-exposure problem.</li>
 *   <li><b>Everything passes through {@link SensitiveDataMasker}.</b> This
 *       aspect was writing raw card numbers from {@code PaymentRequest} and
 *       plaintext passwords from {@code LoginRequest} to stdout.</li>
 *   <li><b>Exceptions log with the throwable</b>, not just its message, so the
 *       stack trace reaches the {@code stack_trace} field.</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
public class LoggingAspect {

    /** Calls slower than this are surfaced at WARN so they are alertable. */
    @Value("${pos.logging.slow-call-threshold-ms:1000}")
    private long slowCallThresholdMs;

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void controllerMethods() {
    }

    @Pointcut("within(@org.springframework.stereotype.Service *)")
    public void serviceMethods() {
    }

    @Before("controllerMethods()")
    public void logControllerMethodEntry(JoinPoint joinPoint) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug("Request received {} {}",
                StructuredArguments.keyValue("operation", operationOf(joinPoint)),
                StructuredArguments.keyValue("args", maskedArgs(joinPoint)));
    }

    @AfterReturning(pointcut = "controllerMethods()", returning = "result")
    public void logControllerMethodExit(JoinPoint joinPoint, Object result) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug("Request completed {} {}",
                StructuredArguments.keyValue("operation", operationOf(joinPoint)),
                StructuredArguments.keyValue("result", SensitiveDataMasker.mask(String.valueOf(result))));
    }

    @AfterThrowing(pointcut = "controllerMethods() || serviceMethods()", throwing = "exception")
    public void logException(JoinPoint joinPoint, Throwable exception) {
        // The throwable is passed as the final argument so Logback renders the
        // full stack trace into the stack_trace field. The original version
        // logged only getMessage(), discarding it.
        log.error("Unhandled exception in {} {}",
                StructuredArguments.keyValue("operation", operationOf(joinPoint)),
                StructuredArguments.keyValue("exceptionType", exception.getClass().getSimpleName()),
                exception);
    }

    @Around("serviceMethods()")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            String operation = operationOf(joinPoint);

            if (elapsedMs >= slowCallThresholdMs) {
                log.warn("Slow service call {} {}",
                        StructuredArguments.keyValue("operation", operation),
                        StructuredArguments.keyValue(LoggingConstants.DURATION_MS, elapsedMs));
            } else if (log.isDebugEnabled()) {
                log.debug("Service call {} {}",
                        StructuredArguments.keyValue("operation", operation),
                        StructuredArguments.keyValue(LoggingConstants.DURATION_MS, elapsedMs));
            }
        }
    }

    /** {@code CheckService.addItem} rather than the fully-qualified signature. */
    private String operationOf(JoinPoint joinPoint) {
        return joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
    }

    private String maskedArgs(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return "[]";
        }
        return SensitiveDataMasker.mask(Arrays.toString(args));
    }
}
