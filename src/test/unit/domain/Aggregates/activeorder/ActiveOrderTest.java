package unit.domain.Aggregates.activeorder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import domain.Aggregates.activeorder.ActiveOrder;

import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class ActiveOrderTest {

    private ActiveOrder activeOrder;

    @BeforeEach
    void setUp() {
        activeOrder = new ActiveOrder("1", "10", "5", 10);
    }

    @Test
    void givenValidTicket_whenAddTicket_thenIncreasesSubtotalAndAddsId() {
        activeOrder.addTicket("99", 50.0);
        activeOrder.addTicket("100", 75.0);

        assertEquals(2, activeOrder.getTicketIds().size());
        assertEquals(125.0, activeOrder.getTotalPrice());
    }

    @Test
    void givenTicketExists_whenRemoveTicket_thenDecreasesSubtotalAndReturnsTrue() {
        activeOrder.addTicket("99", 50.0);
        boolean removed = activeOrder.removeTicket("99", 50.0);

        assertTrue(removed);
        assertEquals(0, activeOrder.getTicketIds().size());
        assertEquals(0.0, activeOrder.getTotalPrice());
    }

    @Test
    void givenTicketDoesNotExist_whenRemoveTicket_thenReturnsFalse() {
        boolean removed = activeOrder.removeTicket("99", 50.0);
        assertFalse(removed);
    }

    @Test
    void givenDiscount_whenGetTotalPrice_thenAppliesDiscountCorrectly() {
        activeOrder.addTicket("1", 100.0);
        activeOrder.setDiscountAmount(20.0);
        
        assertEquals(80.0, activeOrder.getTotalPrice());
    }

    @Test
    void givenDiscountLargerThanPrice_whenGetTotalPrice_thenNeverReturnsNegative() {
        activeOrder.addTicket("1", 50.0);
        activeOrder.setDiscountAmount(100.0); // Discount larger than price
        
        assertEquals(0.0, activeOrder.getTotalPrice());
    }

    @Test
    void givenTimeIsPastExpiration_whenIsExpired_thenReturnsTrue() {
        LocalDateTime futureTime = LocalDateTime.now().plusMinutes(15);
        assertTrue(activeOrder.isExpired(futureTime));
    }

    @Test
    void givenTimeIsBeforeExpiration_whenIsExpired_thenReturnsFalse() {
        LocalDateTime currentTime = LocalDateTime.now().plusMinutes(5);
        assertFalse(activeOrder.isExpired(currentTime));
    }
}
