package infra.external;

import infra.config.ExternalServicesProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Low-level HTTP client for the WSEP external payment / ticket-issuance system.
 *
 * <p>Every supported action ({@code handshake}, {@code pay}, {@code refund},
 * {@code issue_ticket}, {@code cancel_ticket}) is sent as an
 * {@code application/x-www-form-urlencoded} POST to the configured base URL, as
 * specified in WSEP_External_Systems.pdf.
 *
 * <p><b>Robustness (Version 3, Req. 5 & 6).</b> The external boundary is wrapped with
 * Resilience4j: a {@link Retry} retries transient failures (connection errors, timeouts,
 * non-2xx responses) with a bounded backoff <i>for idempotent actions only</i>
 * ({@code handshake}, {@code refund}, {@code cancel_ticket}); the side-effecting actions
 * {@code pay} and {@code issue_ticket} are attempted exactly once so a lost response can't
 * cause a duplicate charge / double-issued ticket. A {@link CircuitBreaker} trips after the
 * configured failure rate so a sick external system fails fast instead of stalling every
 * request, then recovers automatically once the wait window elapses. Both are driven by
 * {@code ticketanic.external.*} config. No network or parsing failure escapes — callers
 * receive a typed "failed" result ({@code -1} / {@code null} / {@code false}) which higher
 * layers translate into a domain-level failure.
 */
@Component
public class WsepExternalClient {

    private static final Logger logger = LoggerFactory.getLogger(WsepExternalClient.class);

    /** Sentinel returned by the external system to indicate a failed action. */
    public static final int FAILURE_CODE = -1;

    private final HttpClient httpClient;
    private final URI endpoint;
    private final Duration readTimeout;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public WsepExternalClient(ExternalServicesProperties props) {
        this.endpoint = URI.create(props.baseUrl());
        this.readTimeout = Duration.ofMillis(props.readTimeoutMs());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.connectTimeoutMs()))
                .build();

        ExternalServicesProperties.Retry retryCfg = props.retry();
        this.retry = Retry.of("wsep", RetryConfig.custom()
                .maxAttempts(retryCfg.maxAttempts())
                .waitDuration(Duration.ofMillis(Math.max(1, retryCfg.backoffMs())))
                .retryExceptions(TransientExternalException.class)
                .build());

        ExternalServicesProperties.CircuitBreaker cbCfg = props.circuitBreaker();
        this.circuitBreaker = CircuitBreaker.of("wsep", CircuitBreakerConfig.custom()
                .failureRateThreshold(cbCfg.failureRateThreshold())
                .waitDurationInOpenState(Duration.ofSeconds(cbCfg.waitDurationSeconds()))
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(cbCfg.slidingWindowSize())
                .minimumNumberOfCalls(cbCfg.minimumNumberOfCalls())
                .recordExceptions(TransientExternalException.class)
                .build());
    }

    /** @return {@code true} if the external system answers the handshake with "OK". */
    public boolean handshake() {
        String body = post(Map.of("action_type", "handshake"));
        return body != null && body.trim().toUpperCase().contains("OK");
    }

    /**
     * Charges a payment.
     *
     * @return the external transaction id in [10000, 100000], or {@link #FAILURE_CODE} on failure.
     */
    public int pay(PaymentRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type", "pay");
        params.put("amount", request.amount());
        params.put("currency", request.currency());
        params.put("card_number", request.cardNumber());
        params.put("month", request.month());
        params.put("year", request.year());
        params.put("holder", request.holder());
        params.put("cvv", request.cvv());
        params.put("id", request.payerId());
        // Non-idempotent: a charge must never be auto-retried (double-charge risk).
        return parseInt(post(params, false));
    }

    /**
     * Refunds a previously successful transaction.
     *
     * @return {@code true} if the external system confirms the refund (returns 1).
     */
    public boolean refund(int transactionId) {
        String body = post(Map.of(
                "action_type", "refund",
                "transaction_id", Integer.toString(transactionId)));
        return parseInt(body) == 1;
    }

    /**
     * Issues a general-admission / standing ticket batch.
     *
     * @return the secure ticket code (e.g. {@code TIX-1a2b-3456}), or {@code null} on failure.
     */
    public String issueStandingTicket(String customerId, String eventId, String zone, int quantity) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type", "issue_ticket");
        params.put("customer_id", customerId);
        params.put("event_id", eventId);
        params.put("zone", zone);
        params.put("quantity", Integer.toString(quantity));
        // Non-idempotent: issuance must never be auto-retried (double-issue risk).
        return parseTicketCode(post(params, false));
    }

    /**
     * Issues an assigned-seating ticket batch for one zone. Sends {@code is_seating=true}
     * and the {@code seats} parameter as a JSON array of {@code {"row":R,"seat":S}} objects,
     * matching WSEP_External_Systems.pdf.
     *
     * @return the secure ticket code (e.g. {@code TIX-1a2b-3456}), or {@code null} on failure.
     */
    public String issueSeatedTickets(String customerId, String eventId, String zone, List<SeatRef> seats) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type", "issue_ticket");
        params.put("customer_id", customerId);
        params.put("event_id", eventId);
        params.put("zone", zone);
        params.put("is_seating", "true");
        params.put("seats", seatsToJson(seats));
        // Non-idempotent: issuance must never be auto-retried (double-issue risk).
        return parseTicketCode(post(params, false));
    }

    /** Serializes seats as {@code [{"row":R,"seat":S}, ...]} for the {@code seats} parameter. */
    private static String seatsToJson(List<SeatRef> seats) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < seats.size(); i++) {
            SeatRef s = seats.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"row\":").append(s.row()).append(",\"seat\":").append(s.seat()).append('}');
        }
        return sb.append(']').toString();
    }

    /**
     * Cancels an issued ticket (used for system-initiated rollbacks).
     *
     * @return {@code true} if the external system confirms cancellation (returns 1).
     */
    public boolean cancelTicket(String ticketId) {
        String body = post(Map.of(
                "action_type", "cancel_ticket",
                "ticket_id", ticketId));
        return parseInt(body) == 1;
    }

    // ───────────────────────── internals ───────────────────────── //

    /** Idempotent / read-only actions go through the retry; see {@link #post(Map, boolean)}. */
    private String post(Map<String, String> params) {
        return post(params, true);
    }

    /**
     * Sends the form-encoded POST through the circuit breaker (and, for idempotent actions, the
     * retry), returning the response body. Returns {@code null} when the external system is
     * unreachable, times out, answers with a non-2xx status on every attempt, or the breaker is open.
     *
     * <p>{@code retryable} MUST be {@code false} for non-idempotent actions that have an external
     * side effect ({@code pay}, {@code issue_ticket}). Retrying a charge or an issuance after a
     * transient failure (e.g. a response lost to a request the server actually processed) risks a
     * duplicate charge / double-issued ticket. Suppressing the retry there also means a single bad
     * request records one breaker failure instead of three, so a user correcting their card details
     * and retrying isn't pushed past the breaker's failure threshold (issue #426).
     */
    private String post(Map<String, String> params, boolean retryable) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(readTimeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(params)))
                .build();
        String action = params.get("action_type");

        Supplier<String> throughBreaker =
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> executeOnce(request, action));
        Supplier<String> decorated = retryable ? Retry.decorateSupplier(retry, throughBreaker) : throughBreaker;
        try {
            return decorated.get();
        } catch (CallNotPermittedException e) {
            logger.warn("WSEP action '{}' short-circuited: circuit breaker is open", action);
            return null;
        } catch (TransientExternalException e) {
            logger.error("WSEP action '{}' failed: {}", action, e.getMessage());
            return null;
        } catch (RuntimeException e) {
            logger.error("WSEP action '{}' aborted: {}", action, e.toString());
            return null;
        }
    }

    /**
     * One HTTP attempt. Throws {@link TransientExternalException} for non-2xx responses and
     * I/O errors so the retry and circuit breaker count them as failures.
     */
    private String executeOnce(HttpRequest request, String action) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return response.body();
            }
            throw new TransientExternalException("HTTP " + status + " from action '" + action + "'");
        } catch (IOException e) {
            throw new TransientExternalException("I/O error on action '" + action + "': " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Not a TransientExternalException: do not retry or count against the breaker.
            throw new ExternalCallInterruptedException(action, e);
        }
    }

    private static String formEncode(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /** Parses an integer response, returning {@link #FAILURE_CODE} for null/blank/malformed bodies. */
    private static int parseInt(String body) {
        if (body == null) return FAILURE_CODE;
        try {
            return Integer.parseInt(body.trim());
        } catch (NumberFormatException e) {
            logger.warn("WSEP returned non-numeric response '{}'", body);
            return FAILURE_CODE;
        }
    }

    /**
     * Returns the trimmed ticket code, or {@code null} for null/blank/"-1" bodies, or bodies
     * that don't look like a ticket code. The external contract only guarantees a code string
     * on success or "-1" on failure, so we defensively reject anything that isn't a single
     * {@code [A-Za-z0-9-]+} token (e.g. an HTML/error page or "OK" returned with HTTP 200) —
     * "incompatible interface" robustness (Version 3, Req. 5).
     */
    private static String parseTicketCode(String body) {
        if (body == null) return null;
        String code = body.trim();
        if (code.isEmpty() || code.equals("-1")) return null;
        if (!looksLikeTicketCode(code)) {
            logger.warn("WSEP issue_ticket returned an unrecognized body '{}'; treating as failure", body);
            return null;
        }
        return code;
    }

    /**
     * A plausible ticket code is a single token of letters/digits/dashes, at least 4 chars long,
     * containing a dash or a digit (e.g. {@code TIX-1a2b-3456}). This accepts real codes while
     * rejecting HTML/error pages (whitespace/angle brackets) and short prose like {@code "OK"}.
     */
    private static boolean looksLikeTicketCode(String code) {
        if (!code.matches("[A-Za-z0-9-]+") || code.length() < 4) return false;
        return code.indexOf('-') >= 0 || code.chars().anyMatch(Character::isDigit);
    }

    /** Marks a retryable / breaker-recorded failure of an external call. */
    private static final class TransientExternalException extends RuntimeException {
        TransientExternalException(String message) {
            super(message);
        }

        TransientExternalException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Thread interruption during an external call: not retried and not counted by the breaker. */
    private static final class ExternalCallInterruptedException extends RuntimeException {
        ExternalCallInterruptedException(String action, Throwable cause) {
            super("interrupted during action '" + action + "'", cause);
        }
    }

    /**
     * Card / payer details for a {@code pay} action. All values are kept as
     * strings because they are sent verbatim as form parameters.
     */
    public record PaymentRequest(
            String amount,
            String currency,
            String cardNumber,
            String month,
            String year,
            String holder,
            String cvv,
            String payerId
    ) { }

    /** A single assigned seat for an {@code issue_ticket} (seated) request: row + seat number. */
    public record SeatRef(int row, int seat) { }
}
