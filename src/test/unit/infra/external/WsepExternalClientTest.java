package unit.infra.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import infra.config.ExternalServicesProperties;
import infra.external.WsepExternalClient;
import infra.external.WsepExternalClient.PaymentRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Resilience tests for {@link WsepExternalClient} (Version 3, Req. 5).
 *
 * <p>All requests target an in-process {@link HttpServer} on an ephemeral port — never the
 * real Koyeb endpoint — so the suite is hermetic and safe to run in CI.
 */
class WsepExternalClientTest {

    private HttpServer server;

    private final PaymentRequest samplePayment = new PaymentRequest(
            "1000", "USD", "2222333344445555", "4", "2030", "Ticketanic", "262", "20444444");

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    // ───────────────────────── happy paths ───────────────────────── //

    @Test
    void handshakeReturnsTrueOnOk() {
        WsepExternalClient client = clientFor(fixedResponse(200, "OK"));
        assertTrue(client.handshake());
    }

    @Test
    void handshakeReturnsFalseOnNonOkBody() {
        WsepExternalClient client = clientFor(fixedResponse(200, "DOWN"));
        assertFalse(client.handshake());
    }

    @Test
    void payReturnsTransactionId() {
        WsepExternalClient client = clientFor(fixedResponse(200, "54321"));
        assertEquals(54321, client.pay(samplePayment));
    }

    @Test
    void payReturnsFailureCodeWhenDeclined() {
        WsepExternalClient client = clientFor(fixedResponse(200, "-1"));
        assertEquals(WsepExternalClient.FAILURE_CODE, client.pay(samplePayment));
    }

    @Test
    void payReturnsFailureCodeOnMalformedBody() {
        WsepExternalClient client = clientFor(fixedResponse(200, "not-a-number"));
        assertEquals(WsepExternalClient.FAILURE_CODE, client.pay(samplePayment));
    }

    @Test
    void refundReturnsTrueOnSuccessCode() {
        WsepExternalClient client = clientFor(fixedResponse(200, "1"));
        assertTrue(client.refund(54321));
    }

    @Test
    void refundReturnsFalseOnFailureCode() {
        WsepExternalClient client = clientFor(fixedResponse(200, "-1"));
        assertFalse(client.refund(54321));
    }

    @Test
    void issueStandingTicketReturnsCode() {
        WsepExternalClient client = clientFor(fixedResponse(200, "TIX-1a2b-3456"));
        assertEquals("TIX-1a2b-3456", client.issueStandingTicket("cust", "evt", "Zone A", 2));
    }

    @Test
    void issueStandingTicketReturnsNullOnFailure() {
        WsepExternalClient client = clientFor(fixedResponse(200, "-1"));
        assertNull(client.issueStandingTicket("cust", "evt", "Zone A", 1));
    }

    @Test
    void issueSeatedTicketsSendsIsSeatingAndSeatsJsonAndReturnsCode() {
        StringBuilder captured = new StringBuilder();
        WsepExternalClient client = clientFor(capturingResponse(captured, 200, "TIX-1a2b-3456"));

        String code = client.issueSeatedTickets("cust", "evt", "VIP Balcony",
                List.of(new WsepExternalClient.SeatRef(4, 12), new WsepExternalClient.SeatRef(4, 13)));

        assertEquals("TIX-1a2b-3456", code);
        String body = decode(captured.toString());
        assertTrue(body.contains("action_type=issue_ticket"), body);
        assertTrue(body.contains("is_seating=true"), body);
        assertTrue(body.contains("seats=[{\"row\":4,\"seat\":12},{\"row\":4,\"seat\":13}]"), body);
    }

    @Test
    void issueSeatedTicketsReturnsNullOnFailure() {
        WsepExternalClient client = clientFor(fixedResponse(200, "-1"));
        assertNull(client.issueSeatedTickets("cust", "evt", "VIP Balcony",
                List.of(new WsepExternalClient.SeatRef(1, 1))));
    }

    @Test
    void issueTicketTreatsIncompatibleBodyAsFailure() {
        // A 200 response whose body is not a ticket code (HTML/error page, prose, "OK")
        // must be treated as a failure, not accepted as a fake ticket code (Req 5).
        assertNull(clientFor(fixedResponse(200, "<html>Internal Server Error</html>"))
                .issueStandingTicket("cust", "evt", "Zone A", 1));
        assertNull(clientFor(fixedResponse(200, "Internal Server Error"))
                .issueStandingTicket("cust", "evt", "Zone A", 1));
        assertNull(clientFor(fixedResponse(200, "OK"))
                .issueSeatedTickets("cust", "evt", "VIP Balcony", List.of(new WsepExternalClient.SeatRef(1, 1))));
    }

    @Test
    void cancelTicketReturnsTrueOnSuccessCode() {
        WsepExternalClient client = clientFor(fixedResponse(200, "1"));
        assertTrue(client.cancelTicket("TIX-1a2b-3456"));
    }

    // ───────────────────────── resilience ───────────────────────── //

    @Test
    void retriesIdempotentActionConfiguredNumberOfTimesOnServerError() {
        AtomicInteger requests = new AtomicInteger();
        WsepExternalClient client = clientFor(exchange -> {
            requests.incrementAndGet();
            respond(exchange, 500, "boom");
        }, /*maxAttempts*/ 3);

        // handshake is idempotent → retried; no exception escapes, it collapses to false.
        assertFalse(client.handshake());
        assertEquals(3, requests.get(), "idempotent actions should retry up to the configured attempt count");
    }

    @Test
    void payIsNotRetriedOnServerError() {
        AtomicInteger requests = new AtomicInteger();
        WsepExternalClient client = clientFor(exchange -> {
            requests.incrementAndGet();
            respond(exchange, 500, "boom");
        }, /*maxAttempts*/ 3);

        // pay has an external side effect → attempted exactly once even though maxAttempts=3,
        // so a lost response can't double-charge and a single bad card logs one breaker failure.
        assertEquals(WsepExternalClient.FAILURE_CODE, client.pay(samplePayment));
        assertEquals(1, requests.get(), "pay must be attempted exactly once (no retry)");
    }

    @Test
    void issueTicketIsNotRetriedOnServerError() {
        AtomicInteger requests = new AtomicInteger();
        WsepExternalClient client = clientFor(exchange -> {
            requests.incrementAndGet();
            respond(exchange, 500, "boom");
        }, /*maxAttempts*/ 3);

        // issue_ticket has an external side effect → attempted exactly once (no double-issue).
        assertNull(client.issueStandingTicket("cust", "evt", "Zone A", 1));
        assertEquals(1, requests.get(), "issue_ticket must be attempted exactly once (no retry)");
    }

    @Test
    void unreachableServerYieldsFailureWithoutThrowing() throws IOException {
        // Bind then release a port so nothing is listening — simulates loss of communication.
        int deadPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort();
        }
        WsepExternalClient client = new WsepExternalClient(propsFor("http://localhost:" + deadPort + "/", 3));

        assertFalse(client.handshake());
        assertEquals(WsepExternalClient.FAILURE_CODE, client.pay(samplePayment));
        assertNull(client.issueStandingTicket("cust", "evt", "Zone A", 1));
    }

    @Test
    void slowServerTimesOutAndReportsFailure() {
        WsepExternalClient client = clientFor(exchange -> {
            try {
                Thread.sleep(800); // exceeds the 200ms read timeout configured below
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "12345");
        }, /*maxAttempts*/ 2, /*readTimeoutMs*/ 200);

        assertEquals(WsepExternalClient.FAILURE_CODE, client.pay(samplePayment));
    }

    @Test
    void circuitBreakerOpensAfterFailuresAndShortCircuitsFurtherCalls() {
        AtomicInteger requests = new AtomicInteger();
        String baseUrl = startServer(exchange -> {
            requests.incrementAndGet();
            respond(exchange, 500, "boom");
        });
        // Open after 3 recorded failures; no retries so each pay() == one call.
        WsepExternalClient client = new WsepExternalClient(new ExternalServicesProperties(
                baseUrl, 1000, 5000,
                new ExternalServicesProperties.Retry(1, 0),
                new ExternalServicesProperties.CircuitBreaker(50, 30, 3, 3),
                null, null));

        for (int i = 0; i < 3; i++) {
            assertEquals(WsepExternalClient.FAILURE_CODE, client.pay(samplePayment));
        }
        assertEquals(3, requests.get(), "breaker should still be closed for the first 3 calls");

        // Breaker is now open: further calls fail fast without hitting the server.
        assertEquals(WsepExternalClient.FAILURE_CODE, client.pay(samplePayment));
        assertEquals(WsepExternalClient.FAILURE_CODE, client.pay(samplePayment));
        assertEquals(3, requests.get(), "open breaker must short-circuit and not call the server");
    }

    // ───────────────────────── helpers ───────────────────────── //

    private WsepExternalClient clientFor(HttpHandler handler) {
        return clientFor(handler, 1);
    }

    private WsepExternalClient clientFor(HttpHandler handler, int maxAttempts) {
        return clientFor(handler, maxAttempts, 5000);
    }

    private WsepExternalClient clientFor(HttpHandler handler, int maxAttempts, int readTimeoutMs) {
        String baseUrl = startServer(handler);
        return new WsepExternalClient(propsFor(baseUrl, maxAttempts, readTimeoutMs));
    }

    private String startServer(HttpHandler handler) {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", handler);
            server.start();
            return "http://localhost:" + server.getAddress().getPort() + "/";
        } catch (IOException e) {
            throw new RuntimeException("failed to start mock server", e);
        }
    }

    private static ExternalServicesProperties propsFor(String baseUrl, int maxAttempts) {
        return propsFor(baseUrl, maxAttempts, 5000);
    }

    private static ExternalServicesProperties propsFor(String baseUrl, int maxAttempts, int readTimeoutMs) {
        return new ExternalServicesProperties(
                baseUrl,
                1000,           // connectTimeoutMs
                readTimeoutMs,
                new ExternalServicesProperties.Retry(maxAttempts, 0), // no backoff → fast tests
                null,           // circuit-breaker defaults
                null,           // payment defaults
                null);          // ticket-issuance defaults
    }

    private static HttpHandler fixedResponse(int status, String body) {
        return exchange -> respond(exchange, status, body);
    }

    /** Captures the raw request body into {@code sink}, then responds with {@code body}. */
    private static HttpHandler capturingResponse(StringBuilder sink, int status, String body) {
        return exchange -> {
            sink.append(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        };
    }

    private static String decode(String formEncoded) {
        return URLDecoder.decode(formEncoded, StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String body) {
        try {
            // Drain the request body so the connection can be reused/closed cleanly.
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
