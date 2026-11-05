package unit.domain.Aggregates.ticket;

import org.junit.jupiter.api.Test;

import domain.Aggregates.ticket.Ticket;

import static org.junit.jupiter.api.Assertions.*;

class TicketTest {

    @Test
    void givenSeatIdIsNull_whenIsStanding_thenReturnsTrue() {
        Ticket standingTicket = new Ticket("1", "10", "5", null, 100.0);
        assertTrue(standingTicket.isStanding(), "Ticket should be marked as standing when seatId is null");
    }

    @Test
    void givenSeatIdIsNotNull_whenIsStanding_thenReturnsFalse() {
        Ticket seatedTicket = new Ticket("2", "10", "5", "42", 150.0);
        assertFalse(seatedTicket.isStanding(), "Ticket should not be standing when seatId is provided");
    }
}
