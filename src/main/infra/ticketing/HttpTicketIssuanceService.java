package infra.ticketing;

import application.dto.TicketIssuanceRequestDTO;
import application.interfaces.ITicketIssuanceService;
import infra.config.ExternalServicesProperties;
import infra.external.WsepExternalClient;
import infra.external.WsepExternalClient.SeatRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Real {@link ITicketIssuanceService} backed by the external WSEP ticket-issuance
 * system (issues #284, #335).
 *
 * <p>Active only when {@code ticketanic.external.ticket-issuance.enabled=true}; otherwise
 * {@link StubTicketIssuanceService} is used. Exactly one of the two beans exists.
 *
 * <p>An order's tickets always belong to a single zone, so the whole order is issued with
 * <b>one</b> external {@code issue_ticket} call — {@code quantity} for a standing zone, or
 * a {@code seats} array for assigned seating. The call returns a single ticket code, which is
 * returned to the caller so it can be persisted on the order; {@code OrderService} cancels it
 * via {@link #cancelIssuedTickets} if a later checkout step fails. A failed call returns
 * {@code null} so {@code OrderService} refunds the payment and no order is created — keeping
 * payment and issuance consistent.
 *
 * <p>This adapter is a thin HTTP translator: all customer / event / zone / seat data is
 * resolved upstream by {@code OrderService} and arrives in the {@link TicketIssuanceRequestDTO},
 * so no repository access is needed here. The configured {@code defaultZone} is used only as
 * a safety fallback when a zone name could not be resolved.
 */
@Component
@ConditionalOnProperty(prefix = "ticketanic.external.ticket-issuance", name = "enabled", havingValue = "true")
public class HttpTicketIssuanceService implements ITicketIssuanceService {

    private static final Logger logger = LoggerFactory.getLogger(HttpTicketIssuanceService.class);

    private final WsepExternalClient client;
    private final String defaultZone;

    public HttpTicketIssuanceService(WsepExternalClient client, ExternalServicesProperties props) {
        this.client = client;
        this.defaultZone = props.ticketIssuance().defaultZone();
    }

    @Override
    public String issueTickets(String activeOrderId, TicketIssuanceRequestDTO request) {
        if (request == null) {
            logger.warn("Issuance failed: null request for activeOrderId={}", activeOrderId);
            return null;
        }

        String zone = (request.zoneName() == null || request.zoneName().isBlank())
                ? defaultZone : request.zoneName();

        String code;
        if (request.standing()) {
            logger.info("HttpTicketIssuanceService.issueTickets activeOrderId={} standing quantity={} zone='{}'",
                    activeOrderId, request.quantity(), zone);
            code = client.issueStandingTicket(request.customerId(), request.eventId(), zone, request.quantity());
        } else {
            List<SeatRef> seats = request.seats().stream()
                    .map(s -> new SeatRef(s.row(), s.seat()))
                    .toList();
            logger.info("HttpTicketIssuanceService.issueTickets activeOrderId={} seated seatCount={} zone='{}'",
                    activeOrderId, seats.size(), zone);
            code = client.issueSeatedTickets(request.customerId(), request.eventId(), zone, seats);
        }

        if (code == null) {
            logger.warn("Issuance failed for activeOrderId={} (zone='{}')", activeOrderId, zone);
            return null;
        }

        logger.info("Issued ticket code={} for activeOrderId={}", code, activeOrderId);
        return code;
    }

    @Override
    public boolean cancelIssuedTickets(String externalTicketCode) {
        if (externalTicketCode == null || externalTicketCode.isBlank()) {
            return false;
        }
        boolean cancelled = client.cancelTicket(externalTicketCode);
        if (!cancelled) {
            logger.error("Compensation could not cancel issued ticket code={}", externalTicketCode);
        }
        return cancelled;
    }
}
