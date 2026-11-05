package infra.ticketing;

import application.dto.TicketIssuanceRequestDTO;
import application.interfaces.ITicketIssuanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development stub for {@link ITicketIssuanceService}.
 * Always reports successful issuance. Used when the real external ticket-issuance system
 * is disabled ({@code ticketanic.external.ticket-issuance.enabled=false}, the default in
 * dev/test); {@link HttpTicketIssuanceService} takes over when it is enabled. Exactly one
 * bean exists.
 */
@Component
@ConditionalOnProperty(prefix = "ticketanic.external.ticket-issuance", name = "enabled",
        havingValue = "false", matchIfMissing = true)
public class StubTicketIssuanceService implements ITicketIssuanceService {

    private static final Logger logger = LoggerFactory.getLogger(StubTicketIssuanceService.class);

    @Override
    public String issueTickets(String activeOrderId, TicketIssuanceRequestDTO request) {
        int count = request == null ? 0
                : (request.standing() ? request.quantity() : request.seats().size());
        logger.info("StubTicketIssuanceService.issueTickets activeOrderId={} ticketCount={}",
                activeOrderId, count);
        return "STUB-" + activeOrderId;
    }

    @Override
    public boolean cancelIssuedTickets(String externalTicketCode) {
        logger.info("StubTicketIssuanceService.cancelIssuedTickets code={}", externalTicketCode);
        return true;
    }
}
