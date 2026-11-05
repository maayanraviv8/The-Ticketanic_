package unit.domain.Aggregates.member;

import domain.Aggregates.member.Suspension;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SuspensionTest {

    private Suspension newSuspension() { return new Suspension("test@mail.com", LocalDateTime.now()); }

    // Initial state
    @Test
    void givenNewSuspension_thenIsActive() {
        Suspension suspension = newSuspension();
        assertTrue(suspension.isActive());
        assertNotNull(suspension.getSuspensionDate());
        assertNull(suspension.getEndDate());
    }

    @Test
    void givenNewSuspension_thenSuspensionDateIsBeforeOrEqualToNow() {
        LocalDateTime before = LocalDateTime.now();
        Suspension suspension = newSuspension();
        assertFalse(suspension.getSuspensionDate().isAfter(LocalDateTime.now()));
    }

    // close()
    @Test
    void givenNewSuspension_whenClose_thenIsNotActive() {
        Suspension suspension = newSuspension();
        suspension.close(LocalDateTime.now());
        assertFalse(suspension.isActive());
        assertNotNull(suspension.getEndDate());
    }

    @Test
    void givenNewSuspension_whenClose_thenEndDateMatchesPassedValue() {
        Suspension suspension = newSuspension();
        LocalDateTime end = LocalDateTime.of(2026, 6, 1, 12, 0);
        suspension.close(end);
        assertEquals(end, suspension.getEndDate());
    }

    @Test
    void givenClosedSuspension_whenCloseAgain_thenThrowsIllegalStateException() {
        Suspension suspension = newSuspension();
        suspension.close(LocalDateTime.now());
        assertThrows(IllegalStateException.class, () -> suspension.close(LocalDateTime.now()));
    }
}