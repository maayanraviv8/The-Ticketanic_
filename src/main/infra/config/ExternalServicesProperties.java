package infra.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds the {@code ticketanic.external.*} configuration for the WSEP external
 * payment and ticket-issuance systems (see WSEP_External_Systems.pdf).
 *
 * <p>The {@code base-url} and credentials are configuration-driven (never
 * hard-coded), so tests and dev runs point at a stub/mock and only production
 * talks to the real Koyeb endpoint.
 */
@Validated
@ConfigurationProperties(prefix = "ticketanic.external")
public record ExternalServicesProperties(
        @NotBlank String baseUrl,
        @Min(1) int connectTimeoutMs,
        @Min(1) int readTimeoutMs,
        @Valid Retry retry,
        @Valid CircuitBreaker circuitBreaker,
        @Valid Payment payment,
        @Valid TicketIssuance ticketIssuance
) {
    public ExternalServicesProperties {
        if (connectTimeoutMs == 0) connectTimeoutMs = 3000;
        if (readTimeoutMs == 0) readTimeoutMs = 5000;
        if (retry == null) retry = new Retry(3, 500);
        if (circuitBreaker == null) circuitBreaker = new CircuitBreaker(50, 30, 10, 5);
        if (payment == null) payment = new Payment(true);
        if (ticketIssuance == null) ticketIssuance = new TicketIssuance(true, null);
    }

    public record Retry(@Min(1) int maxAttempts, @Min(0) int backoffMs) { }

    /**
     * Drives the Resilience4j circuit breaker around the external HTTP calls.
     * {@code failureRateThreshold} is a percentage; once at least
     * {@code minimumNumberOfCalls} have been recorded in a count-based window of
     * {@code slidingWindowSize}, the breaker opens for {@code waitDurationSeconds}.
     */
    public record CircuitBreaker(
            @Min(1) int failureRateThreshold,
            @Min(1) int waitDurationSeconds,
            @Min(1) int slidingWindowSize,
            @Min(1) int minimumNumberOfCalls
    ) {
        public CircuitBreaker {
            if (slidingWindowSize == 0) slidingWindowSize = 10;
            if (minimumNumberOfCalls == 0) minimumNumberOfCalls = 5;
        }
    }

    /**
     * Payment toggle. Card / payer details are no longer configured here — they are
     * collected at checkout and carried by {@code PaymentDetailsDTO} (issue #334).
     */
    public record Payment(boolean enabled) { }

    /**
     * Ticket-issuance toggle plus the default zone used when the thin
     * {@link application.interfaces.ITicketIssuanceService} signature does not
     * carry the real event/zone (follow-up threads those through).
     */
    public record TicketIssuance(boolean enabled, String defaultZone) {
        public TicketIssuance {
            if (isBlank(defaultZone)) defaultZone = "General Admission";
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
