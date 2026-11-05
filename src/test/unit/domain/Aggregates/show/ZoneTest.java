package unit.domain.Aggregates.show;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import domain.Aggregates.show.Seat;
import domain.Aggregates.show.Zone;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZoneTest {

    @Mock
    private Seat mockSeat1;
    @Mock
    private Seat mockSeat2;

    private Zone seatedZone;
    private Zone standingZone;

    @BeforeEach
    void setUp() {
        seatedZone = Zone.builder()
                .zoneId("1")
                .standing(false)
                .build();

        lenient().when(mockSeat1.getId()).thenReturn("101");
        lenient().when(mockSeat2.getId()).thenReturn("102");
        seatedZone.addSeat(mockSeat1);
        seatedZone.addSeat(mockSeat2);

        standingZone = Zone.builder()
                .zoneId("2")
                .standing(true)
                .standingCapacity(100)
                .standingAvailable(100)
                .build();
    }

    // ── lockSeats ─────────────────────────────────────────────────────────────

    @Test
    void givenSeatsAreAvailable_whenLockSeats_thenReturnsTrue() {
        when(mockSeat1.isTaken()).thenReturn(false);
        when(mockSeat2.isTaken()).thenReturn(false);

        boolean result = seatedZone.lockSeats(Arrays.asList("101", "102"));

        assertTrue(result);
        verify(mockSeat1).setTaken(true);
        verify(mockSeat2).setTaken(true);
    }

    @Test
    void givenOneSeatIsTaken_whenLockSeats_thenReturnsFalseAndNoSeatsLocked() {
        when(mockSeat1.isTaken()).thenReturn(false);
        when(mockSeat2.isTaken()).thenReturn(true);

        boolean result = seatedZone.lockSeats(Arrays.asList("101", "102"));

        assertFalse(result);
        verify(mockSeat1, never()).setTaken(anyBoolean());
        verify(mockSeat2, never()).setTaken(anyBoolean());
    }

    @Test
    void givenNonExistentSeatId_whenLockSeats_thenReturnsFalse() {
        boolean result = seatedZone.lockSeats(Arrays.asList("999"));
        assertFalse(result);
    }

    @Test
    void givenZoneIsStanding_whenLockSeats_thenReturnsFalse() {
        assertFalse(standingZone.lockSeats(Arrays.asList("101")));
    }

    // ── releaseSeats ──────────────────────────────────────────────────────────

    @Test
    void givenLockedSeats_whenReleaseSeats_thenSetsNotTaken() {
        seatedZone.releaseSeats(Arrays.asList("101"));
        verify(mockSeat1).setTaken(false);
    }

    @Test
    void givenUnknownSeatId_whenReleaseSeats_thenNoSeatModified() {
        seatedZone.releaseSeats(Arrays.asList("999"));
        verify(mockSeat1, never()).setTaken(anyBoolean());
        verify(mockSeat2, never()).setTaken(anyBoolean());
    }

    @Test
    void givenZoneIsStanding_whenReleaseSeats_thenDoesNothing() {
        standingZone.releaseSeats(Arrays.asList("101"));
        verify(mockSeat1, never()).setTaken(anyBoolean());
    }

    // ── lockStandingTickets ───────────────────────────────────────────────────

    @Test
    void givenCapacityExists_whenLockStandingTickets_thenReturnsTrue() {
        boolean result = standingZone.lockStandingTickets(5);

        assertTrue(result);
        assertEquals(95, standingZone.getStandingAvailable());
    }

    @Test
    void givenNotEnoughCapacity_whenLockStandingTickets_thenReturnsFalse() {
        boolean result = standingZone.lockStandingTickets(105);

        assertFalse(result);
        assertEquals(100, standingZone.getStandingAvailable());
    }

    @Test
    void givenZoneIsSeated_whenLockStandingTickets_thenReturnsFalse() {
        assertFalse(seatedZone.lockStandingTickets(5));
    }

    // ── releaseStandingTickets ────────────────────────────────────────────────

    @Test
    void givenAvailableSlots_whenReleaseStandingTickets_thenIncreasesAvailable() {
        standingZone.lockStandingTickets(10);
        standingZone.releaseStandingTickets(5);

        assertEquals(95, standingZone.getStandingAvailable());
    }

    @Test
    void givenReleaseExceedsCapacity_whenReleaseStandingTickets_thenCapsAtCapacity() {
        standingZone.releaseStandingTickets(50);

        assertEquals(100, standingZone.getStandingAvailable());
    }

    @Test
    void givenZoneIsSeated_whenReleaseStandingTickets_thenDoesNothing() {
        seatedZone.releaseStandingTickets(5);
        // No exception, no state change in a seated zone
    }
}
