package unit.infra.scheduling;

import application.event.RaffleConfiguredEvent;
import application.service.EventService;
import domain.Aggregates.event.Event;
import domain.Aggregates.event.Raffle;
import domain.Aggregates.event.RaffleStatus;
import domain.Irepository.IEventRepository;
import infra.scheduling.RaffleDrawScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RaffleDrawScheduler}: the one-shot draw fires at the deadline, the
 * safety-net sweep re-attempts every raffle event, and startup recovery (re)schedules pending
 * raffles (overdue ones fire promptly). The draw itself is faked via a recording
 * {@link EventService} so we assert only the scheduling behavior.
 */
class RaffleDrawSchedulerTest {

    private ThreadPoolTaskScheduler taskScheduler;

    @BeforeEach
    void setUp() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.initialize();
    }

    @AfterEach
    void tearDown() {
        taskScheduler.shutdown();
    }

    @Test
    void onRaffleConfigured_schedulesDrawThatFiresAtDeadline() throws Exception {
        CountDownLatch fired = new CountDownLatch(1);
        RecordingEventService svc = new RecordingEventService(fired);
        RaffleDrawScheduler scheduler = new RaffleDrawScheduler(mock(IEventRepository.class), svc, taskScheduler);

        scheduler.onRaffleConfigured(new RaffleConfiguredEvent(
                "e1", LocalDateTime.now().plus(150, ChronoUnit.MILLIS)));

        assertTrue(fired.await(2, TimeUnit.SECONDS), "draw should fire at the deadline");
        assertEquals(List.of("e1"), svc.drawn);
    }

    @Test
    void sweepDueRaffles_invokesEnsureForEachRaffleEvent() {
        RecordingEventService svc = new RecordingEventService(null);
        IEventRepository repo = mock(IEventRepository.class);
        when(repo.findRaffleEnabledEvents()).thenReturn(List.of(eventWithId("a"), eventWithId("b")));
        RaffleDrawScheduler scheduler = new RaffleDrawScheduler(repo, svc, taskScheduler);

        scheduler.sweepDueRaffles();

        assertEquals(List.of("a", "b"), svc.drawn);
    }

    @Test
    void rescheduleOnStartup_drawsOverduePendingRafflePromptly() throws Exception {
        CountDownLatch fired = new CountDownLatch(1);
        RecordingEventService svc = new RecordingEventService(fired);
        IEventRepository repo = mock(IEventRepository.class);
        Event overdue = eventWithPendingRaffle("e9", LocalDateTime.now().minusMinutes(1));
        when(repo.findRaffleEnabledEvents()).thenReturn(List.of(overdue));
        RaffleDrawScheduler scheduler = new RaffleDrawScheduler(repo, svc, taskScheduler);

        scheduler.rescheduleOnStartup();

        assertTrue(fired.await(2, TimeUnit.SECONDS), "overdue raffle should be drawn promptly on startup");
        assertEquals(List.of("e9"), svc.drawn);
    }

    @Test
    void rescheduleOnStartup_skipsAlreadyDrawnRaffles() {
        RecordingEventService svc = new RecordingEventService(null);
        IEventRepository repo = mock(IEventRepository.class);
        Event drawn = eventWithPendingRaffle("done", LocalDateTime.now().minusMinutes(1));
        drawn.getRaffle().setStatus(RaffleStatus.DRAWN); // no longer pending
        when(repo.findRaffleEnabledEvents()).thenReturn(List.of(drawn));
        RaffleDrawScheduler scheduler = new RaffleDrawScheduler(repo, svc, taskScheduler);

        scheduler.rescheduleOnStartup();

        assertEquals(List.of(), svc.drawn);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Event eventWithId(String id) {
        return Event.builder().eventId(id).build();
    }

    private static Event eventWithPendingRaffle(String id, LocalDateTime deadline) {
        Raffle raffle = Raffle.builder()
                .raffleId("r-" + id)
                .eventId(id)
                .registrationDeadline(deadline)
                .codeValidity(Duration.ofMinutes(30))
                .numberOfWinners(1)
                .status(RaffleStatus.OPEN)
                .build();
        return Event.builder().eventId(id).raffle(raffle).build();
    }

    /** Records the event ids passed to {@code ensureRaffleDrawn} instead of performing a real draw. */
    private static final class RecordingEventService extends EventService {
        final List<String> drawn = Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch fired;

        RecordingEventService(CountDownLatch fired) {
            super(null, null, null, null, null, null, null, null, null);
            this.fired = fired;
        }

        @Override
        public void ensureRaffleDrawn(String eventId) {
            drawn.add(eventId);
            if (fired != null) fired.countDown();
        }
    }
}
