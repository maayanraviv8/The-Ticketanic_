package application.service;

import application.dto.*;
import application.event.RaffleConfiguredEvent;
import application.factories.DiscountPolicyFactory;
import application.factories.PurchasePolicyFactory;
import application.interfaces.IAuth;
import application.interfaces.INotificationService;
import domain.Aggregates.event.Event;
import domain.Aggregates.productioncompany.Permissions;
import domain.Aggregates.productioncompany.ProductionCompany;
import domain.Irepository.IEventRepository;
import domain.Irepository.IOrderRepository;
import domain.Irepository.IProductionCompanyRepository;
import domain.Irepository.IShowRepository;
import domain.Aggregates.show.Show;
import domain.exceptions.DuplicateEventException;
import domain.exceptions.EventNotFoundException;
import domain.exceptions.InvalidEventException;
import domain.exceptions.ShowNotFoundException;
import domain.exceptions.UnauthorizedException;
import domain.policies.discount.IDiscountRule;
import domain.policies.purchase.IPurchaseRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class EventService {

    private static final Logger logger = LoggerFactory.getLogger(EventService.class);

    private final IEventRepository eventRepository;
    private final IAuth authService;
    private final IProductionCompanyRepository companyRepository;
    private final INotificationService notificationService;
    private final IOrderRepository orderRepository;
    private final IShowRepository showRepository;
    private final DiscountPolicyFactory discountPolicyFactory;
    private final PurchasePolicyFactory purchasePolicyFactory;
    private final ApplicationEventPublisher eventPublisher;

    /** Per-event monitors so a lazy raffle draw runs exactly once under concurrent access. */
    private final ConcurrentHashMap<String, Object> drawLocks = new ConcurrentHashMap<>();

    public EventService(IEventRepository eventRepository, IAuth authService,
                        IProductionCompanyRepository companyRepository,
                        INotificationService notificationService,
                        IOrderRepository orderRepository,
                        IShowRepository showRepository,
                        DiscountPolicyFactory discountPolicyFactory,
                        PurchasePolicyFactory purchasePolicyFactory,
                        ApplicationEventPublisher eventPublisher) {
        this.eventRepository = eventRepository;
        this.authService = authService;
        this.companyRepository = companyRepository;
        this.notificationService = notificationService;
        this.orderRepository = orderRepository;
        this.showRepository = showRepository;
        this.discountPolicyFactory = discountPolicyFactory;
        this.purchasePolicyFactory = purchasePolicyFactory;
        this.eventPublisher = eventPublisher;
    }

    private String authenticate(String token) {
        if (token == null || !authService.validateToken(token))
            throw new UnauthorizedException("Token is invalid or has expired");
        String userId = authService.extractEmailFromToken(token);
        if (userId == null)
            throw new UnauthorizedException("Could not identify user from token");
        return userId;
    }

    ///////// Raffle / purchase-right lottery (II.3.9, II.4.3) /////////

    /**
     * UC II.4.3 — Owner/Manager sets an event's sale mode to raffle. Gated by
     * {@link Permissions#EVENT_EDITING} (raffle is configured as part of event/inventory setup).
     * The {@code registrationDeadline} doubles as the sale-open moment.
     */
    public EventDTO configureRaffle(String eventId, LocalDateTime registrationDeadline,
                                    Duration codeValidity, int numberOfWinners, String token) {
        String userId = authenticate(token);
        Event event = eventRepository.findById(eventId);
        if (event == null)
            throw new EventNotFoundException(eventId);

        ProductionCompany company = companyRepository.findById(event.getProductionCompanyName());
        if (company == null || !company.isActive())
            throw new IllegalArgumentException("Cannot configure raffle: production company not found or not active.");
        if (!company.memberHasPermission(userId, Permissions.EVENT_EDITING))
            throw new UnauthorizedException("You do not have permission to configure the sale mode for this event.");

        event.configureRaffle(UUID.randomUUID().toString(), registrationDeadline, codeValidity,
                numberOfWinners, LocalDateTime.now());
        eventRepository.store(event);
        logger.info("Configured raffle for event id={} (registration deadline {}, {} winner(s))",
                eventId, registrationDeadline, numberOfWinners);
        // Hand off to the scheduling layer to fire the draw at the deadline (a republish for a
        // reconfigured deadline reschedules). Decoupled via an event to avoid a circular dependency
        // on RaffleDrawScheduler, which itself depends on this service's ensureRaffleDrawn.
        eventPublisher.publishEvent(new RaffleConfiguredEvent(eventId, registrationDeadline));
        return EventDTO.fromEntity(event);
    }

    ///////// UC10 - Register for Raffle /////////

    /** A logged-in member pre-registers for a raffle. Guests (no token) are rejected by {@link #authenticate}. */
    public void registerForRaffle(String eventId, String token) {
        String memberId = authenticate(token);
        // Serialize per-event so concurrent registrations don't race on the participant set.
        synchronized (lockFor(eventId)) {
            Event event = eventRepository.findById(eventId);
            if (event == null)
                throw new EventNotFoundException(eventId);
            event.registerForRaffle(memberId, LocalDateTime.now());
            eventRepository.store(event);
        }
        logger.info("Member '{}' registered for raffle on event id={}", memberId, eventId);
    }

    private Object lockFor(String eventId) {
        return drawLocks.computeIfAbsent(eventId, k -> new Object());
    }

    /**
     * Returns the caller's own raffle status. The draw itself is driven in real time by
     * {@code RaffleDrawScheduler}; this read just reflects the current state.
     */
    public RaffleStatusDTO getRaffleStatus(String eventId, String token) {
        String memberId = authenticate(token);
        Event event = eventRepository.findById(eventId);
        if (event == null)
            throw new EventNotFoundException(eventId);

        return RaffleStatusDTO.fromEntity(event, memberId);
    }

    /**
     * Draw chokepoint, driven in real time by {@code RaffleDrawScheduler}: if the sale has opened
     * (registration deadline passed) and winners have not yet been drawn, draw up to the configured
     * number of winners, persist, and notify every participant of the outcome — winners get their unique time-limited code,
     * losers get a "not selected" notice. Runs the draw exactly once even under concurrent access. Safe
     * to call with any event id (no-op for unknown/non-raffle/not-yet-due raffles). Takes an id rather
     * than an {@link Event} so the entity is always (re)loaded inside this transaction — callers such as
     * the scheduler hold detached entities whose lazy raffle would otherwise fail to initialize.
     */
    public void ensureRaffleDrawn(String eventId) {
        if (eventId == null)
            return;

        synchronized (lockFor(eventId)) {
            // Read and check under the lock so only the first thread performs the draw. The entity is
            // fetched here, inside the transaction, so its lazy raffle association initializes correctly
            // (acting on a detached entity would throw LazyInitializationException under the JPA profile).
            Event fresh = eventRepository.findById(eventId);
            if (fresh == null || !fresh.isRaffleDrawDue(LocalDateTime.now()))
                return;

            Set<String> winners = fresh.drawRaffleWinners(
                    () -> UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                    LocalDateTime.now());
            eventRepository.store(fresh);

            Set<String> losers = new LinkedHashSet<>(fresh.getRaffleParticipants());
            losers.removeAll(winners);
            logger.info("Drew raffle for event id={}: {} winner(s), {} non-winner(s)",
                    fresh.getEventId(), winners.size(), losers.size());

            for (String email : winners) {
                notificationService.sendNotification(email,
                        "Raffle Winner: " + fresh.getName(),
                        "You won the raffle for '" + fresh.getName()
                            + "'. Your access code is " + fresh.getWinnerCode(email)
                            + " (valid until " + fresh.getWinnerCodeExpiry(email) + ").",
                        fresh.getProductionCompanyName());
            }
            for (String email : losers) {
                notificationService.sendNotification(email,
                        "Raffle Result: " + fresh.getName(),
                        "The raffle for '" + fresh.getName()
                            + "' has been drawn and unfortunately you were not selected this time.",
                        fresh.getProductionCompanyName());
            }
        }
    }

    // ── UC14 - Manage Event (create, edit, remove) - including inventory management

    private void authorizeEventEditing(String userId, String companyId) {
        ProductionCompany company = companyRepository.findById(companyId);
        if (company == null || !company.isActive())
            throw new IllegalArgumentException("Production company '" + companyId + "' not found or not active.");
        boolean authorized = company.memberIsFounder(userId)
                || company.memberIsOwner(userId)
                || company.memberHasPermission(userId, Permissions.EVENT_EDITING);
        if (!authorized)
            throw new UnauthorizedException("You do not have permission to manage events for this company.");
    }

    public void authorizeHallLayoutManagement(String showId, String userId) {
        Show show = showRepository.findById(showId);
        if (show == null) throw new ShowNotFoundException(showId);
        Event event = eventRepository.findById(show.getEventId());
        if (event == null) throw new EventNotFoundException(show.getEventId());
        authorizeEventEditing(userId, event.getProductionCompanyName());
    }

    public EventDTO createEvent(EventDTO eventDTO, String productionCompanyId, String token) {
        String userId = authenticate(token);
        logger.info("User '{}' creating event '{}' for company id={}", userId, eventDTO.name, productionCompanyId);
        validateEventDetails(eventDTO);

        authorizeEventEditing(userId, productionCompanyId);
        if (eventRepository.existsByNameAndProductionCompany(eventDTO.name, productionCompanyId))
            throw new DuplicateEventException(eventDTO.name);

        Event event = Event.builder()
                .name(eventDTO.name)
                .productionCompanyName(productionCompanyId)
                .managerId(userId)
                .startDate(eventDTO.startDate)
                .endDate(eventDTO.endDate)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        event.setEventTypeName(eventDTO.eventTypeName);

        eventRepository.store(event);
        logger.info("Successfully created event id={} '{}'", event.getEventId(), event.getName());
        return EventDTO.fromEntity(event);
    }

    public EventDTO editEvent(String eventId, EventDTO eventDTO, String token) {
        String userId = authenticate(token);
        logger.info("User '{}' editing event id={}", userId, eventId);

        Event existing = eventRepository.findById(eventId);
        if (existing == null)
            throw new EventNotFoundException(eventId);

        authorizeEventEditing(userId, existing.getProductionCompanyName());
        validateEventDetails(eventDTO);
        // A raffle's winner count is independent of event capacity (#439), so capacity may be
        // edited freely (even below the winner count or to zero) regardless of any raffle.

        existing.setName(eventDTO.name);
        existing.setEventTypeName(eventDTO.eventTypeName);
        existing.setStartDate(eventDTO.startDate);
        existing.setEndDate(eventDTO.endDate);
        existing.setUpdatedAt(LocalDateTime.now());

        eventRepository.updateEventInventory(eventId, existing);
        logger.info("Successfully updated event id={}", eventId);
        return EventDTO.fromEntity(existing);
    }

    public void removeEvent(String eventId, String token) {
        String userId = authenticate(token);
        logger.info("User '{}' removing event id={}", userId, eventId);

        Event existing = eventRepository.findById(eventId);
        if (existing == null)
            throw new EventNotFoundException(eventId);

        authorizeEventEditing(userId, existing.getProductionCompanyName());
        showRepository.findByEventId(eventId).forEach(show -> showRepository.delete(show.getShowId()));
        eventRepository.delete(eventId);
        logger.info("Successfully removed event id={}", eventId);
    }

    public EventDTO getEventById(String eventId) {
        logger.info("Fetching event id={}", eventId);
        Event event = eventRepository.findById(eventId);
        if (event == null)
            throw new EventNotFoundException(eventId);
        return EventDTO.fromEntity(event);
    }

    // ── UC16 - Event Policies ─────────────────────────────────────────────────

    public EventDTO setEventPurchasePolicy(String eventId, PurchasePolicyDTO policyDTO, String token) {
        String userId = authenticate(token);
        logger.info("User '{}' setting event policy for event id={}", userId, eventId);

        Event existing = eventRepository.findById(eventId);
        if (existing == null)
            throw new EventNotFoundException(eventId);

        ProductionCompany company = companyRepository.findById(existing.getProductionCompanyName());
        if (company == null || !company.memberHasPermission(userId, Permissions.CHANGE_PURCHASE_POLICY)) {
            logger.error("setEventPurchasePolicy: User '{}' lacks permission for event id={} under company '{}'",
                    userId, eventId, existing.getProductionCompanyName());
            throw new UnauthorizedException("Unauthorized: You do not have permission to manage this event's policies.");
        }

        IPurchaseRule domainPurchaseRule = purchasePolicyFactory.createFromDTO(policyDTO);

        existing.setPurchasePolicy(domainPurchaseRule);

        eventRepository.store(existing);

        return EventDTO.fromEntity(existing);
    }

    public EventDTO setDiscountPolicy(String eventId, DiscountPolicyDTO policyDTO, String token) {
        String userId = authenticate(token);
        logger.info("User '{}' setting discount policy for event id={}", userId, eventId);

        Event existing = eventRepository.findById(eventId);
        if (existing == null)
            throw new EventNotFoundException(eventId);

        ProductionCompany company = companyRepository.findById(existing.getProductionCompanyName());
        if (company == null || !company.memberHasPermission(userId, Permissions.CHANGE_DISCOUNT_POLICY)) {
            logger.error("setEventPurchasePolicy: User '{}' lacks permission for event id={} under company '{}'",
                    userId, eventId, existing.getProductionCompanyName());
            throw new UnauthorizedException("Unauthorized: You do not have permission to manage this event's policies.");
        }

        IDiscountRule domainDiscountRule = discountPolicyFactory.createFromDTO(policyDTO);

        existing.setDiscountPolicy(domainDiscountRule);

        eventRepository.store(existing);

        return EventDTO.fromEntity(existing);
    }

    // ── UC17 - View Event Purchase History ───────────────────────────────────

    public List<OrderDTO> getPurchaseHistoryByProductionCompany(String productionCompanyId, LocalDateTime startDate,
            LocalDateTime endDate, String token) {
        String userId = authenticate(token);
        authorizeViewPurchaseHistory(userId, productionCompanyId);
        logger.info("User '{}' viewing purchase history for company id={} in date range", userId, productionCompanyId);
        List<String> eventIds = eventRepository.findByProductionCompanyId(productionCompanyId)
                .stream().map(Event::getEventId).toList();
        return eventIds.stream()
                .flatMap(eid -> orderRepository.findByEventIdAndDateRange(eid, startDate, endDate).stream())
                .map(OrderDTO::fromEntity)
                .toList();
    }

    private void authorizeViewPurchaseHistory(String userId, String companyId) {
        ProductionCompany company = companyRepository.findById(companyId);
        if (company == null)
            throw new IllegalArgumentException("Production company not found: " + companyId);
        boolean authorized = company.memberIsFounder(userId)
                || company.memberIsOwner(userId)
                || company.memberHasPermission(userId, Permissions.VIEW_PURCHASE_HISTORY);
        if (!authorized) {
            logger.warn("User '{}' not authorized to view purchase history for company '{}'", userId, companyId);
            throw new UnauthorizedException("You do not have permission to view purchase history for this company.");
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validateEventDetails(EventDTO eventDTO) {
        if (eventDTO.name == null || eventDTO.name.isBlank())
            throw new InvalidEventException("Event name must not be empty.");
        if (eventDTO.startDate == null)
            throw new InvalidEventException("Event start date must not be null.");
        if (eventDTO.endDate == null)
            throw new InvalidEventException("Event end date must not be null.");
        if (!eventDTO.endDate.isAfter(eventDTO.startDate))
            throw new InvalidEventException("Event end date must be after start date.");
    }

    // ── UC3 - Event search ───────────────────────────────────
    public List<EventDTO> searchEvents(String productionCompanyName, List<String> keywords,
            String category, LocalDateTime startDate, LocalDateTime endDate,
            String location, Double minPrice, Double maxPrice) {

        logger.info(
                "Searching events - company: '{}', keywords: '{}', category: '{}', location: '{}', dates: '{} - {}', price: '{} - {}'",
                productionCompanyName, keywords, category, location, startDate, endDate, minPrice, maxPrice);

        List<Event> results = eventRepository.searchEvents(
                productionCompanyName, keywords, category,
                startDate, endDate, location, minPrice, maxPrice);

        if (results.isEmpty()) {
            logger.info("No events found matching the selected criteria");
        } else {
            logger.info("Found {} event(s) matching search criteria", results.size());
        }

        return results.stream()
                .map(EventDTO::fromEntity)
                .toList();
    }

    public List<EventDTO> getEventsByProductionCompany(String productionCompanyId) {
        logger.info("Fetching events for company id={}", productionCompanyId);
        return eventRepository.findByProductionCompanyId(productionCompanyId)
                .stream().map(EventDTO::fromEntity).toList();
    }

    public DiscountPolicyDTO getDiscountPolicy(String eventID) {
        logger.info("Getting discount policy for event id={}", eventID);
        Event event = eventRepository.findById(eventID);
        if (event == null)
            throw new EventNotFoundException(eventID);
        return DiscountPolicyDTO.fromEntity(event.getDiscountPolicy());
    }

    public PurchasePolicyDTO getEventPurchasePolicy(String eventId) {
        logger.info("Getting purchase policy for event id={}", eventId);
        Event event = eventRepository.findById(eventId);
        if (event == null)
            throw new EventNotFoundException(eventId);
        return PurchasePolicyDTO.fromEntity(event.getPurchasePolicy());
    }

}
