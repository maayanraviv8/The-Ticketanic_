package unit.domain.Aggregates.event;

import domain.Aggregates.event.Raffle;
import domain.Aggregates.event.RaffleStatus;
import domain.exceptions.RaffleException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class RaffleTest {

    private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 6, 1, 12, 0);
    private static final LocalDateTime BEFORE = DEADLINE.minusHours(1);
    private static final LocalDateTime AFTER = DEADLINE.plusHours(1);

    private Raffle openRaffle() {
        return Raffle.builder()
                .raffleId("r1")
                .eventId("e1")
                .registrationDeadline(DEADLINE)
                .codeValidity(Duration.ofMinutes(30))
                .status(RaffleStatus.OPEN)
                .build();
    }

    /** Deterministic sequential code generator for assertions. */
    private Supplier<String> sequentialCodes() {
        AtomicInteger n = new AtomicInteger();
        return () -> "CODE" + n.incrementAndGet();
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void givenOpenRaffleBeforeDeadline_whenRegister_thenParticipantAdded() {
        Raffle raffle = openRaffle();
        raffle.register("user1", BEFORE);
        assertTrue(raffle.isParticipant("user1"));
    }

    @Test
    void givenPastDeadline_whenRegister_thenThrows() {
        Raffle raffle = openRaffle();
        assertThrows(RaffleException.class, () -> raffle.register("user1", AFTER));
        assertFalse(raffle.isParticipant("user1"));
    }

    @Test
    void givenDuplicate_whenRegister_thenThrows() {
        Raffle raffle = openRaffle();
        raffle.register("user1", BEFORE);
        assertThrows(RaffleException.class, () -> raffle.register("user1", BEFORE));
        assertEquals(1, raffle.getParticipants().size());
    }

    @Test
    void givenAlreadyDrawn_whenRegister_thenThrows() {
        Raffle raffle = openRaffle();
        raffle.register("user1", BEFORE);
        raffle.drawWinners(10, sequentialCodes(), AFTER);
        assertThrows(RaffleException.class, () -> raffle.register("user2", BEFORE));
    }

    // ── isDrawDue ─────────────────────────────────────────────────────────────

    @Test
    void givenBeforeDeadline_whenIsDrawDue_thenFalse() {
        assertFalse(openRaffle().isDrawDue(BEFORE));
    }

    @Test
    void givenAtOrAfterDeadlineAndOpen_whenIsDrawDue_thenTrue() {
        assertTrue(openRaffle().isDrawDue(DEADLINE));
        assertTrue(openRaffle().isDrawDue(AFTER));
    }

    @Test
    void givenAlreadyDrawn_whenIsDrawDue_thenFalse() {
        Raffle raffle = openRaffle();
        raffle.drawWinners(10, sequentialCodes(), AFTER);
        assertFalse(raffle.isDrawDue(AFTER));
    }

    // ── drawWinners ───────────────────────────────────────────────────────────

    @Test
    void givenMoreParticipantsThanWinners_whenDraw_thenWinnersCappedAtNumberOfWinners() {
        Raffle raffle = openRaffle();
        raffle.register("a", BEFORE);
        raffle.register("b", BEFORE);
        raffle.register("c", BEFORE);

        Set<String> winners = raffle.drawWinners(2, sequentialCodes(), AFTER);

        assertEquals(2, winners.size());
        assertEquals(RaffleStatus.DRAWN, raffle.getStatus());
    }

    @Test
    void givenWinners_whenDraw_thenEachHasUniqueCodeExpiringAfterValidity() {
        Raffle raffle = openRaffle();
        raffle.register("a", BEFORE);
        raffle.register("b", BEFORE);

        raffle.drawWinners(10, sequentialCodes(), AFTER);

        String codeA = raffle.getAuthorization("a").getCode();
        String codeB = raffle.getAuthorization("b").getCode();
        assertNotNull(codeA);
        assertNotNull(codeB);
        assertNotEquals(codeA, codeB);
        assertEquals(AFTER.plusMinutes(30), raffle.getAuthorization("a").getExpiresAt());
    }

    @Test
    void givenZeroParticipants_whenDraw_thenNoWinnersButDrawn() {
        Raffle raffle = openRaffle();
        Set<String> winners = raffle.drawWinners(10, sequentialCodes(), AFTER);
        assertTrue(winners.isEmpty());
        assertEquals(RaffleStatus.DRAWN, raffle.getStatus());
    }

    @Test
    void givenAlreadyDrawn_whenDrawAgain_thenThrows() {
        Raffle raffle = openRaffle();
        raffle.register("a", BEFORE);
        raffle.drawWinners(10, sequentialCodes(), AFTER);
        assertThrows(RaffleException.class, () -> raffle.drawWinners(10, sequentialCodes(), AFTER));
    }

    // ── validateCode ──────────────────────────────────────────────────────────

    @Test
    void givenWinnerWithValidCode_whenValidate_thenTrue() {
        Raffle raffle = openRaffle();
        raffle.register("a", BEFORE);
        raffle.drawWinners(10, sequentialCodes(), AFTER);
        String code = raffle.getAuthorization("a").getCode();
        assertTrue(raffle.validateCode(code, "a", AFTER.plusMinutes(1)));
    }

    @Test
    void givenWrongMemberForCode_whenValidate_thenFalse() {
        Raffle raffle = openRaffle();
        raffle.register("a", BEFORE);
        raffle.register("b", BEFORE);
        raffle.drawWinners(10, sequentialCodes(), AFTER);
        String aCode = raffle.getAuthorization("a").getCode();
        assertFalse(raffle.validateCode(aCode, "b", AFTER.plusMinutes(1)));
    }

    @Test
    void givenExpiredCode_whenValidate_thenFalse() {
        Raffle raffle = openRaffle();
        raffle.register("a", BEFORE);
        raffle.drawWinners(10, sequentialCodes(), AFTER);
        String code = raffle.getAuthorization("a").getCode();
        assertFalse(raffle.validateCode(code, "a", AFTER.plusMinutes(31)));
    }

    @Test
    void givenNotDrawn_whenValidate_thenFalse() {
        Raffle raffle = openRaffle();
        raffle.register("a", BEFORE);
        assertFalse(raffle.validateCode("anything", "a", BEFORE));
    }
}
