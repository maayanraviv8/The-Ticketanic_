package application.interfaces;

import application.dto.TicketIssuanceRequestDTO;

public interface ITicketIssuanceService {

    /**
     * Issues all tickets of an order through the external ticket-issuance system.
     *
     * @param activeOrderId id of the active order being checked out (used for logging / traceability)
     * @param request       fully-resolved customer / event / zone / seat data for the order's single zone
     * @return the external ticket code on success, or {@code null} if issuance failed
     *         (caller refunds the payment)
     */
    String issueTickets(String activeOrderId, TicketIssuanceRequestDTO request);

    /**
     * Cancels a previously issued ticket batch (compensation when a later checkout step fails).
     *
     * @param externalTicketCode the code returned by {@link #issueTickets}
     * @return {@code true} if the external system confirmed the cancellation
     */
    boolean cancelIssuedTickets(String externalTicketCode);
}
