package application.dto;

import domain.Aggregates.event.Event;
import domain.Aggregates.event.Raffle;
import domain.Aggregates.event.RaffleStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the raffle fields of {@link EventDTO#fromEntity}. These mappings were silently dropped in the
 * #230 policy refactor, which made every event look non-raffle to the UI (no badge, no raffle panel) — see #245.
 */
class EventDTOTest {

    @Test
    void givenRaffleEvent_whenFromEntity_thenRequiresRaffleAndStatusMapped() {
        Event event = Event.builder()
                .name("Ultra Music Festival")
                .raffle(Raffle.builder()
                        .raffleId(UUID.randomUUID().toString())
                        .registrationDeadline(LocalDateTime.now().plusDays(5))
                        .status(RaffleStatus.OPEN)
                        .build())
                .build();

        EventDTO dto = EventDTO.fromEntity(event);

        assertTrue(dto.requiresRaffle, "raffle event must map requiresRaffle=true");
        assertEquals("OPEN", dto.raffleStatus, "raffle status must be exposed for public display");
    }

    @Test
    void givenRegularEvent_whenFromEntity_thenNotRaffleAndNullStatus() {
        Event event = Event.builder().name("Regular Show").build();

        EventDTO dto = EventDTO.fromEntity(event);

        assertFalse(dto.requiresRaffle, "non-raffle event must map requiresRaffle=false");
        assertNull(dto.raffleStatus, "non-raffle event must have null raffleStatus");
    }
}
