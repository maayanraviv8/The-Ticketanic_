package application.dto;

import java.util.List;

/**
 * Fully-resolved request for issuing an order's tickets through the external WSEP
 * {@code issue_ticket} action (issue #335).
 *
 * <p>An active order's tickets always belong to a single zone, so a whole order maps to
 * <b>one</b> external call: a standing zone uses {@link #quantity}, a seated zone uses the
 * {@link #seats} list ({@code is_seating=true}). The orchestrating service resolves all
 * domain data (customer, event, zone name, seat row/number) and hands this ready-to-send
 * carrier to the infrastructure adapter, which therefore needs no repository access.
 *
 * <p>Pure data container — no business logic.
 *
 * @param customerId external {@code customer_id} (member email, or payer id for guests)
 * @param eventId    external {@code event_id}
 * @param zoneName   external {@code zone} (the zone's display name)
 * @param standing   {@code true} for a general-admission zone, {@code false} for assigned seating
 * @param quantity   number of standing tickets (used only when {@code standing})
 * @param seats      assigned seats (used only when not {@code standing})
 */
public record TicketIssuanceRequestDTO(
        String customerId,
        String eventId,
        String zoneName,
        boolean standing,
        int quantity,
        List<SeatRef> seats) {

    /** A single assigned seat: its row and seat number within the zone. */
    public record SeatRef(int row, int seat) { }
}
