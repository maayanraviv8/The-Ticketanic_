package infra.scheduling;

import application.event.RaffleConfiguredEvent;
import application.service.EventService;
import domain.Aggregates.event.Event;
import domain.Irepository.IEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Drives raffle draws in real time. Instead of polling every raffle event on a tight interval, each
 * raffle gets a single one-shot job submitted to a {@link TaskScheduler} to fire at its registration
 * deadline (the sale-open moment), at which point the draw runs via
 * {@link EventService#ensureRaffleDrawn(String)}.
 *
 * <p>Robustness is preserved by two backstops, since the in-memory jobs are lost on restart and a
 * one-shot failure won't re-run itself:
 * <ul>
 *   <li><b>Startup recovery</b> — on {@link ApplicationReadyEvent}, every still-pending raffle is
 *       (re)scheduled; overdue ones fire immediately.</li>
 *   <li><b>Safety-net sweep</b> — a low-frequency {@link Scheduled} pass calls
 *       {@code ensureRaffleDrawn} for every raffle event, catching missed/failed/crashed draws.</li>
 * </ul>
 *
 * <p>The draw itself is idempotent and per-event locked inside {@code EventService}, so duplicate or
 * overlapping fires (one-shot + sweep, or multiple startups) draw and notify exactly once.
 */
@Component
@Slf4j
public class RaffleDrawScheduler {

    private final IEventRepository eventRepository;
    private final EventService eventService;
    private final TaskScheduler taskScheduler;

    /** One in-flight draw job per event, so a reconfigured deadline can cancel and replace it. */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> jobs = new ConcurrentHashMap<>();

    public RaffleDrawScheduler(IEventRepository eventRepository, EventService eventService,
                               TaskScheduler taskScheduler) {
        this.eventRepository = eventRepository;
        this.eventService = eventService;
        this.taskScheduler = taskScheduler;
    }

    /**
     * Schedules (or reschedules) the one-shot draw when a raffle is configured at runtime. Runs
     * after the configuring transaction commits so the scheduled draw can never read a raffle that
     * isn't durably persisted yet; {@code fallbackExecution} keeps it working when there is no
     * surrounding transaction.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRaffleConfigured(RaffleConfiguredEvent event) {
        scheduleDraw(event.eventId(), event.registrationDeadline());
    }

    /**
     * Submits a one-shot draw job for {@code eventId} at {@code deadline}, replacing any job already
     * scheduled for that event. A deadline in the past schedules immediately.
     */
    public void scheduleDraw(String eventId, LocalDateTime deadline) {
        if (eventId == null || deadline == null)
            return;
        Instant when = deadline.atZone(ZoneId.systemDefault()).toInstant();
        ScheduledFuture<?> future = taskScheduler.schedule(() -> runDraw(eventId), when);
        ScheduledFuture<?> previous = jobs.put(eventId, future);
        if (previous != null)
            previous.cancel(false);
        log.debug("Scheduled raffle draw for event id={} at {}", eventId, deadline);
    }

    private void runDraw(String eventId) {
        jobs.remove(eventId);
        try {
            eventService.ensureRaffleDrawn(eventId);
        } catch (Exception ex) {
            // A one-shot won't retry itself; the safety-net sweep is the backstop.
            log.error("Scheduled raffle draw failed for event id={}; will be retried by the sweep", eventId, ex);
        }
    }

    /**
     * Low-frequency backstop: re-attempts any due draw the one-shot jobs missed (crash, transient
     * failure, or a deadline that elapsed while the app was down). Idempotent — a no-op for raffles
     * that are not yet due or already drawn.
     */
    @Scheduled(fixedDelayString = "${raffle.draw.safety-sweep-ms:120000}")
    public void sweepDueRaffles() {
        for (Event event : eventRepository.findRaffleEnabledEvents()) {
            try {
                eventService.ensureRaffleDrawn(event.getEventId());
            } catch (Exception ex) {
                log.error("Safety-net raffle draw failed for event id={}", event.getEventId(), ex);
            }
        }
    }

    /**
     * Rebuilds the one-shot jobs lost on restart: schedules a draw for every raffle whose winners
     * have not been drawn yet. Overdue raffles fire immediately; the sweep covers anything missed
     * between startup and the first scheduled fire.
     */
    @Transactional(readOnly = true)
    @EventListener(ApplicationReadyEvent.class)
    public void rescheduleOnStartup() {
        int rescheduled = 0;
        for (Event event : eventRepository.findRaffleEnabledEvents()) {
            if (event.isRafflePendingDraw()) {
                scheduleDraw(event.getEventId(), event.getRaffleRegistrationDeadline());
                rescheduled++;
            }
        }
        log.info("Rescheduled {} pending raffle draw(s) on startup", rescheduled);
    }
}
