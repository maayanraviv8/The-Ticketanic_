package acceptance;

import application.dto.EventDTO;
import application.dto.ShowDTO;
import application.dto.VenueMapDTO;
import application.service.EventService;
import application.service.MemberService;
import application.service.ProductionCompanyService;
import application.service.ShowService;
import domain.Aggregates.event.Event;
import domain.Aggregates.show.Show;
import domain.Aggregates.show.VenueMap;
import infra.DbStartupVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that when the DB layer throws during a write, the system state is
 * identical to what it was before the failing call — no partial mutations.
 *
 * Each test:
 *  1. Seeds the minimum required state via the real memory repos.
 *  2. Wraps the relevant repo in a stub that throws on the specific write.
 *  3. Asserts the exception propagates.
 *  4. Reads back from the *real* repos to confirm nothing changed.
 */
public class DbResilienceTest extends AcceptanceTestBase {

    private MemberService memberService;
    private EventService eventService;
    private ShowService showService;
    private ProductionCompanyService companyService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepo, orderRepo, ticketRepo,
                passwordEncoder, authService, notificationService);
        eventService = new EventService(eventRepo, authService, companyRepo,
                notificationService, orderRepo, showRepo, discountPolicyFactory, purchasePolicyFactory, pubEvent -> {});
        showService = new ShowService(showRepo, authService, eventService);
        companyService = new ProductionCompanyService(companyRepo, authService,
                eventRepo, notificationService, discountPolicyFactory, purchasePolicyFactory);
    }

    // ── Member ──────────────────────────────────────────────────────────────

    @Test
    void givenDbFailsOnMemberStore_whenRegister_thenNoMemberIsPersisted() {
        MemberService serviceUnderTest = new MemberService(
                new StoreFailingMemberRepo(memberRepo), orderRepo, ticketRepo,
                passwordEncoder, authService, notificationService);

        assertThrows(RuntimeException.class,
                () -> serviceUnderTest.register(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD));

        assertNull(memberRepo.findByEmail(VALID_EMAIL));
    }

    // ── ProductionCompany ────────────────────────────────────────────────────

    @Test
    void givenDbFailsOnCompanyStore_whenOpenProductionCompany_thenNoCompanyIsPersisted() {
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        ProductionCompanyService serviceUnderTest = new ProductionCompanyService(
                new StoreFailingCompanyRepo(companyRepo), authService,
                eventRepo, notificationService, discountPolicyFactory, purchasePolicyFactory);

        assertThrows(RuntimeException.class,
                () -> serviceUnderTest.openProductionCompany(VALID_COMPANY_NAME, token));

        assertNull(companyRepo.findById(VALID_COMPANY_NAME));
    }

    // ── Event ────────────────────────────────────────────────────────────────

    @Test
    void givenDbFailsOnEventStore_whenCreateEvent_thenNoEventIsPersisted() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        EventDTO dto = createValidEventDTO();

        EventService serviceUnderTest = new EventService(
                new StoreFailingEventRepo(eventRepo), authService, companyRepo,
                notificationService, orderRepo, showRepo, discountPolicyFactory, purchasePolicyFactory, pubEvent -> {});

        assertThrows(RuntimeException.class,
                () -> serviceUnderTest.createEvent(dto, VALID_COMPANY_NAME, token));

        assertTrue(eventRepo.findByProductionCompanyId(VALID_COMPANY_NAME).isEmpty());
    }

    @Test
    void givenDbFailsOnEventDelete_whenRemoveEvent_thenEventIsStillPresent() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        EventService serviceUnderTest = new EventService(
                new DeleteFailingEventRepo(eventRepo), authService, companyRepo,
                notificationService, orderRepo, showRepo, discountPolicyFactory, purchasePolicyFactory, pubEvent -> {});

        assertThrows(RuntimeException.class,
                () -> serviceUnderTest.removeEvent(event.getEventId(), token));

        assertNotNull(eventRepo.findById(event.getEventId()));
    }

    // ── Show ─────────────────────────────────────────────────────────────────

    @Test
    void givenDbFailsOnShowStore_whenCreateShow_thenNoShowIsPersisted() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        ShowDTO dto = new ShowDTO();
        dto.date = VALID_END_DATE;

        ShowService serviceUnderTest = new ShowService(
                new StoreFailingShowRepo(showRepo), authService, eventService);

        assertThrows(RuntimeException.class,
                () -> serviceUnderTest.createShow(event.getEventId(), dto, token));

        assertTrue(showRepo.findByEventId(event.getEventId()).isEmpty());
    }

    @Test
    void givenDbFailsOnVenueMapSave_whenDefineHallLayout_thenNoVenueMapIsPersisted() {
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 100);
        VenueMapDTO venueMapDTO = createSeatedVenueMapDTO(show.getShowId(), 10, 10, 50.0);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        ShowService serviceUnderTest = new ShowService(
                new VenueMapSaveFailingShowRepo(showRepo), authService, eventService);

        assertThrows(RuntimeException.class,
                () -> serviceUnderTest.defineHallLayout(show.getShowId(), venueMapDTO, token));

        assertNull(showRepo.getVenueMapByShowId(show.getShowId()));
    }

    @Test
    void givenDbFailsOnVenueMapUpdate_whenUpdateHallLayout_thenOriginalVenueMapIsPreserved() {
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 100);
        showRepo.saveVenueMapLayout(show.getShowId(), new VenueMap());
        VenueMapDTO updatedMap = createSeatedVenueMapDTO(show.getShowId(), 10, 10, 75.0);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        ShowService serviceUnderTest = new ShowService(
                new VenueMapUpdateFailingShowRepo(showRepo), authService, eventService);

        assertThrows(RuntimeException.class,
                () -> serviceUnderTest.updateHallLayout(show.getShowId(), updatedMap, token));

        VenueMap persisted = showRepo.getVenueMapByShowId(show.getShowId());
        assertNotNull(persisted);
        assertEquals(0, persisted.getTotalCapacity());
    }

    @Test
    void givenDbFailsOnVenueMapDelete_whenRemoveHallLayout_thenVenueMapIsStillPresent() {
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 100);
        showRepo.saveVenueMapLayout(show.getShowId(), new VenueMap());
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        ShowService serviceUnderTest = new ShowService(
                new VenueMapDeleteFailingShowRepo(showRepo), authService, eventService);

        assertThrows(RuntimeException.class,
                () -> serviceUnderTest.removeHallLayout(show.getShowId(), token));

        assertNotNull(showRepo.getVenueMapByShowId(show.getShowId()));
    }

    // ── DbStartupVerifier ────────────────────────────────────────────────────

    /** Subclass that skips the sleep so tests don't wait 5 seconds per retry. */
    private static class InstantRetryVerifier extends DbStartupVerifier {
        InstantRetryVerifier(JdbcTemplate jdbc) { super(jdbc); }
        @Override protected void sleep(long ms) { /* no-op */ }
    }

    @Test
    void givenDbAvailableImmediately_whenVerifierRuns_thenStartsSuccessfully() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(1);
        when(jdbc.queryForObject(contains("information_schema.tables"), eq(Integer.class), any()))
                .thenReturn(1);

        DbStartupVerifier verifier = new InstantRetryVerifier(jdbc);
        assertDoesNotThrow(() -> verifier.run(null));

        verify(jdbc, times(1)).queryForObject(eq("SELECT 1"), eq(Integer.class));
    }

    @Test
    void givenDbFailsThenRecovers_whenVerifierRuns_thenStartsSuccessfully() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AtomicInteger callCount = new AtomicInteger(0);
        when(jdbc.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenAnswer(inv -> {
            if (callCount.incrementAndGet() < DbStartupVerifier.MAX_ATTEMPTS) {
                throw new TransientDataAccessResourceException("connection refused");
            }
            return 1;
        });
        when(jdbc.queryForObject(contains("information_schema.tables"), eq(Integer.class), any()))
                .thenReturn(1);

        DbStartupVerifier verifier = new InstantRetryVerifier(jdbc);
        assertDoesNotThrow(() -> verifier.run(null));

        verify(jdbc, times(DbStartupVerifier.MAX_ATTEMPTS))
                .queryForObject(eq("SELECT 1"), eq(Integer.class));
    }

    @Test
    void givenDbNeverRecovers_whenVerifierRuns_thenThrowsAfterAllAttempts() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq("SELECT 1"), eq(Integer.class)))
                .thenThrow(new TransientDataAccessResourceException("connection refused"));

        DbStartupVerifier verifier = new InstantRetryVerifier(jdbc);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> verifier.run(null));

        assertTrue(ex.getMessage().contains(String.valueOf(DbStartupVerifier.MAX_ATTEMPTS)));
        verify(jdbc, times(DbStartupVerifier.MAX_ATTEMPTS))
                .queryForObject(eq("SELECT 1"), eq(Integer.class));
    }

}
