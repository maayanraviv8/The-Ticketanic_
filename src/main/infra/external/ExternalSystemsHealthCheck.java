package infra.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs the result of a {@code handshake} against the external WSEP system at startup, so an
 * unreachable or misbehaving external system is visible in the logs without blocking boot.
 *
 * <p>Active only when the external integrations are enabled (both toggles {@code true}, the
 * production default; stays off in dev/test where they are {@code false}). Intentionally non-fatal
 * (Version 3, Req. 6): the application starts even if the handshake fails, and recovers
 * automatically once the external system is reachable again.
 */
@Component
@ConditionalOnProperty(prefix = "ticketanic.external", name = {"payment.enabled", "ticket-issuance.enabled"},
        havingValue = "true", matchIfMissing = false)
public class ExternalSystemsHealthCheck {

    private static final Logger logger = LoggerFactory.getLogger(ExternalSystemsHealthCheck.class);

    private final WsepExternalClient client;

    public ExternalSystemsHealthCheck(WsepExternalClient client) {
        this.client = client;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkOnStartup() {
        if (client.handshake()) {
            logger.info("External WSEP system handshake OK");
        } else {
            logger.warn("External WSEP system handshake FAILED at startup; the system will keep "
                    + "running and retry per request");
        }
    }
}
