package unit.domain.Aggregates.show;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import domain.Aggregates.show.Seat;
import domain.Aggregates.show.VenueMap;
import domain.Aggregates.show.Zone;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VenueMapTest {

    @Mock
    private Zone mockZone1;
    @Mock
    private Zone mockZone2;

    private VenueMap venueMap;

    @BeforeEach
    void setUp() {
        venueMap = VenueMap.builder().build();

        lenient().when(mockZone1.getZoneId()).thenReturn("1");
        lenient().when(mockZone2.getZoneId()).thenReturn("2");

        venueMap.addZone(mockZone1);
        venueMap.addZone(mockZone2);
    }

    // ── lockSeatsInZone ───────────────────────────────────────────────────────

    @Test
    void givenSeatsAreAvailable_whenLockSeatsInZone_thenRoutesToCorrectZoneAndReturnsTrue() {
        List<String> seatIds = Arrays.asList("101", "102");
        when(mockZone1.lockSeats(seatIds)).thenReturn(true);

        boolean result = venueMap.lockSeatsInZone("1", seatIds);

        assertTrue(result);
        verify(mockZone1).lockSeats(seatIds);
        verify(mockZone2, never()).lockSeats(any());
    }

    @Test
    void givenZoneNotFound_whenLockSeatsInZone_thenReturnsFalse() {
        boolean result = venueMap.lockSeatsInZone("99", Arrays.asList("101"));
        assertFalse(result);
    }

    // ── releaseSeatsInZone ────────────────────────────────────────────────────

    @Test
    void givenValidZone_whenReleaseSeatsInZone_thenRoutesToCorrectZone() {
        List<String> seatIds = Arrays.asList("101");

        venueMap.releaseSeatsInZone("1", seatIds);

        verify(mockZone1).releaseSeats(seatIds);
        verify(mockZone2, never()).releaseSeats(any());
    }

    @Test
    void givenZoneNotFound_whenReleaseSeatsInZone_thenNoZoneCalled() {
        venueMap.releaseSeatsInZone("99", Arrays.asList("101"));

        verify(mockZone1, never()).releaseSeats(any());
        verify(mockZone2, never()).releaseSeats(any());
    }

    // ── lockStandingTicketsInZone ─────────────────────────────────────────────

    @Test
    void givenCapacityExists_whenLockStandingTicketsInZone_thenRoutesToCorrectZoneAndReturnsTrue() {
        when(mockZone2.lockStandingTickets(5)).thenReturn(true);

        boolean result = venueMap.lockStandingTicketsInZone("2", 5);

        assertTrue(result);
        verify(mockZone2).lockStandingTickets(5);
        verify(mockZone1, never()).lockStandingTickets(anyInt());
    }

    @Test
    void givenZoneNotFound_whenLockStandingTicketsInZone_thenReturnsFalse() {
        boolean result = venueMap.lockStandingTicketsInZone("99", 5);
        assertFalse(result);
    }

    // ── releaseStandingTicketsInZone ──────────────────────────────────────────

    @Test
    void givenValidZone_whenReleaseStandingTicketsInZone_thenRoutesToCorrectZone() {
        venueMap.releaseStandingTicketsInZone("2", 10);

        verify(mockZone2).releaseStandingTickets(10);
        verify(mockZone1, never()).releaseStandingTickets(anyInt());
    }

    @Test
    void givenZoneNotFound_whenReleaseStandingTicketsInZone_thenNoZoneCalled() {
        venueMap.releaseStandingTicketsInZone("99", 10);

        verify(mockZone1, never()).releaseStandingTickets(anyInt());
        verify(mockZone2, never()).releaseStandingTickets(anyInt());
    }

    // ── getSeatById ───────────────────────────────────────────────────────────

    @Test
    void givenSeatedZoneWithSeat_whenGetSeatById_thenReturnsSeat() {
        Seat seat = mock(Seat.class);
        when(seat.getId()).thenReturn("101");
        when(mockZone1.isStanding()).thenReturn(false);
        when(mockZone1.getSeats()).thenReturn(List.of(seat));

        Seat result = venueMap.getSeatById("1", "101");

        assertNotNull(result);
        assertEquals("101", result.getId());
    }

    @Test
    void givenStandingZone_whenGetSeatById_thenReturnsNull() {
        when(mockZone1.isStanding()).thenReturn(true);

        Seat result = venueMap.getSeatById("1", "101");

        assertNull(result);
    }

    @Test
    void givenZoneNotFound_whenGetSeatById_thenReturnsNull() {
        Seat result = venueMap.getSeatById("99", "101");
        assertNull(result);
    }

    @Test
    void givenSeatNotInZone_whenGetSeatById_thenReturnsNull() {
        Seat seat = mock(Seat.class);
        when(seat.getId()).thenReturn("999");
        when(mockZone1.isStanding()).thenReturn(false);
        when(mockZone1.getSeats()).thenReturn(List.of(seat));

        Seat result = venueMap.getSeatById("1", "101");

        assertNull(result);
    }

    // ── UC4: View Show Map (read operations) ─────────────────────────────────

    @Test
    void givenValidZoneId_whenGetZoneById_thenReturnsCorrectZone() {
        Zone result = venueMap.getZoneById("1");

        assertNotNull(result, "Should find zone by valid ID");
        assertEquals(mockZone1, result, "Should return the correct zone");
    }

    @Test
    void givenInvalidZoneId_whenGetZoneById_thenReturnsNull() {
        Zone result = venueMap.getZoneById("99");

        assertNull(result, "Should return null for an invalid zone ID");
    }

    @Test
    void givenVenueMapWithZones_whenGetZones_thenReturnsAllZones() {
        List<Zone> zones = venueMap.getZones();

        assertEquals(2, zones.size(), "Should return all zones in the map");
        assertTrue(zones.contains(mockZone1), "Should contain zone 1");
        assertTrue(zones.contains(mockZone2), "Should contain zone 2");
    }

    @Test
    void givenNewVenueMap_whenGetZones_thenReturnsEmptyList() {
        VenueMap emptyMap = VenueMap.builder().build();

        assertTrue(emptyMap.getZones().isEmpty(), "A new venue map with no zones should return an empty list");
    }
}

