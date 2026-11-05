package application.service;

import application.dto.ActiveOrderDTO;

import domain.Aggregates.activeorder.ActiveOrder;
import domain.Aggregates.event.Event;
import domain.Aggregates.show.Show;
import domain.Aggregates.ticket.Ticket;
import domain.Aggregates.member.Member;
import domain.Irepository.IActiveOrderRepository;
import domain.Irepository.IEventRepository;
import domain.Irepository.IMemberRepository;
import domain.Irepository.IShowRepository;
import domain.Irepository.ITicketRepository;
import domain.Irepository.IVirtualQueueRepository;
import domain.exceptions.UnauthorizedException;
import domain.Aggregates.virtualqueue.VirtualQueue;

import java.time.LocalDateTime;
import application.interfaces.IAuth;
import infra.config.ActiveOrderProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional
public class ActiveOrderService {
    private IActiveOrderRepository activeOrderRepository;
    private IEventRepository eventRepository;
    private IShowRepository showRepository;
    private ITicketRepository ticketRepository;
    private IMemberRepository memberRepository;
    private IVirtualQueueRepository virtualQueueRepository;
    private final IAuth authService;
    private final ActiveOrderProperties activeOrderProperties;

    public ActiveOrderService(IActiveOrderRepository activeOrderRepo,
            IEventRepository eventRepo,
            IShowRepository showRepo,
            ITicketRepository ticketRepo,
            IMemberRepository memberRepo,
            IVirtualQueueRepository virtualQueueRepository,
            IAuth authService,
            ActiveOrderProperties activeOrderProperties) {
        this.activeOrderRepository = activeOrderRepo;
        this.eventRepository = eventRepo;
        this.showRepository = showRepo;
        this.ticketRepository = ticketRepo;
        this.memberRepository = memberRepo;
        this.virtualQueueRepository = virtualQueueRepository;
        this.authService = authService;
        this.activeOrderProperties = activeOrderProperties;
    }

    @Transactional
    public ActiveOrderDTO reserveTickets(String showId, String token, String guestId, String raffleCode,
            String zoneId, List<String> seatIds, int standingQuantity) throws Exception {
        int retryCount = 0;
        int maxRetries = activeOrderProperties.maxOptimisticRetries();

        while (true) {
            try {
                return reserveTicketsAttempt(showId, token, guestId, raffleCode, zoneId, seatIds, standingQuantity);
            } catch (domain.exceptions.OptimisticLockException | domain.exceptions.OptimisticLockingFailureException e) {
                retryCount++;
                if (retryCount >= maxRetries) {
                    log.error("Ticket reservation failed after {} retries due to optimistic locking conflicts", maxRetries);
                    throw e;
                }
                log.warn("Optimistic locking conflict detected for Show ID: {}. Retrying... (Attempt {})", showId, retryCount);
            }
        }
    }

    private ActiveOrderDTO reserveTicketsAttempt(String showId, String token, String guestId, String raffleCode,
            String zoneId, List<String> seatIds, int standingQuantity) throws Exception {
        log.info("Initiating ticket reservation attempt for Show ID: {}", showId);

        Member member = validateTokenAndGetMember(token);
        if (member != null) member.ensureNotSuspended();
        validateMemberHasNoActiveOrder(member);

        Show show = loadShowOrThrow(showId);
        Event event = loadEventOrThrow(show.getEventId());

        String queueIdentifier = resolveQueueIdentifier(member, guestId);
        VirtualQueue queue = virtualQueueRepository.findById(showId);

        if (event.requiresRaffle()) {
            // Raffle authorization replaces the virtual-queue gate for raffle events:
            // only a winner holding a valid, unexpired code may enter the reservation phase.
            // The draw itself is performed in real time by RaffleDrawScheduler.
            event.authorizeRaffleReservation(
                member == null ? null : member.getEmail(), raffleCode, LocalDateTime.now());
        } else {
            // Security Check: Enforce Virtual Queue uniformly for members and guests.
            // The frontend routes users through the queue before showing the seat map.
            // Hitting this API directly without an allowed slot is rejected.
            queue.requireAllowed(queueIdentifier);
        }

        List<Ticket> generatedTickets = show.reserveTickets(zoneId, seatIds, standingQuantity,
            () -> java.util.UUID.randomUUID().toString());

        // Save the Show to trigger Optimistic Locking check
        showRepository.store(show);

        // Create the ActiveOrder containing the locked tickets
        ActiveOrder order = new ActiveOrder(
            java.util.UUID.randomUUID().toString(), event.getEventId(), showId,
            activeOrderProperties.lockDurationMinutes());
        order.addTickets(generatedTickets);
        for (Ticket t : generatedTickets) ticketRepository.store(t);

        // Link the active order if identified as a Member
        if (member != null) {
            member.assignActiveOrder(order.getId());
            memberRepository.store(member);
            log.info("Successfully linked Active Order ID: {} to Member Email: {}", order.getId(), member.getEmail());
        }

        // Save order
        activeOrderRepository.store(order);

        // Remove user from the allowed queue session since they successfully started their active order checkout timer
        queue.completeSession(queueIdentifier);
        virtualQueueRepository.store(queue);

        log.info("Successfully created Active Order ID: {} with {} tickets", order.getId(),
                generatedTickets.size());
        return ActiveOrderDTO.fromEntity(order);
    }

    /**
     * Returns the id of the member's current unexpired active order, or {@code null} if
     * the token is invalid/unknown, the member has no linked active order, or the order
     * has expired. Used by the UI to surface a "resume reservation" affordance.
     */
    @Transactional
    public String getActiveOrderIdForMember(String token) {
        try {
            Member member = validateTokenAndGetMember(token);
            if (member == null || !member.hasActiveOrder()) {
                return null;
            }
            ActiveOrder order = activeOrderRepository.findById(member.getActiveOrderId());
            if (order == null || order.isExpired(java.time.LocalDateTime.now())) {
                return null;
            }
            return order.getId();
        } catch (UnauthorizedException e) {
            return null;
        }
    }

    @Transactional
    public ActiveOrderDTO getActiveOrder(String activeOrderId) throws Exception {
        ActiveOrder order = activeOrderRepository.findById(activeOrderId);
        if (order == null) {
            log.warn("Active order not found: {}", activeOrderId);
            throw new Exception("Active order not found");
        }
        return ActiveOrderDTO.fromEntity(order);
    }

    @Transactional
    public ActiveOrderDTO addTicketsToOrder(String orderId, String token,
            String zoneId, List<String> seatIds, int standingQuantity) throws Exception {
        int retryCount = 0;
        int maxRetries = activeOrderProperties.maxOptimisticRetries();
        while (true) {
            try {
                return addTicketsToOrderAttempt(orderId, token, zoneId, seatIds, standingQuantity);
            } catch (domain.exceptions.OptimisticLockException | domain.exceptions.OptimisticLockingFailureException e) {
                retryCount++;
                if (retryCount >= maxRetries) {
                    log.error("Add-tickets failed after {} retries due to optimistic locking conflicts", maxRetries);
                    throw e;
                }
                log.warn("Optimistic locking conflict while adding tickets to Order ID: {}. Retrying... (Attempt {})", orderId, retryCount);
            }
        }
    }

    @Transactional
    private ActiveOrderDTO addTicketsToOrderAttempt(String orderId, String token,
            String zoneId, List<String> seatIds, int standingQuantity) throws Exception {
        log.info("Initiating add-tickets attempt for Order ID: {}", orderId);

        Member member = validateTokenAndGetMember(token);
        if (member != null) member.ensureNotSuspended();

        ActiveOrder order = activeOrderRepository.findById(orderId);
        if (order == null) {
            log.warn("Add-tickets aborted: Order ID {} not found", orderId);
            throw new Exception("Active order not found");
        }
        if (order.isExpired(LocalDateTime.now())) {
            log.warn("Add-tickets aborted: Order ID {} has expired", orderId);
            throw new Exception("Your reservation has expired.");
        }
        // A non-null member here means the JWT was valid (token = session), i.e. logged in.
        if (member != null && !orderId.equals(member.getActiveOrderId())) {
            log.warn("Add-tickets aborted: Order ID {} not owned by member {}", orderId, member.getEmail());
            throw new UnauthorizedException("This order does not belong to the current user.");
        }

        Show show = loadShowOrThrow(order.getShowId());

        // Single-zone-per-order rule: refuse to mix zones in one ActiveOrder.
        // Sampling the first ticket is sufficient because this very guard keeps
        // every other ticket in the order in the same zone.
        if (!order.getTicketIds().isEmpty()) {
            Ticket existing = ticketRepository.findById(order.getTicketIds().get(0));
            if (existing != null && !existing.getZoneId().equals(zoneId)) {
                String zoneName = show.getZoneName(existing.getZoneId());
                log.warn("Add-tickets aborted: Order ID {} is locked to zone '{}'; cannot add from zone '{}'",
                        orderId, existing.getZoneId(), zoneId);
                throw new Exception("Your order already has tickets in zone '" + zoneName
                        + "'. Remove all of them before adding tickets from a different zone.");
            }
        }

        List<Ticket> generatedTickets = show.reserveTickets(zoneId, seatIds, standingQuantity,
                () -> java.util.UUID.randomUUID().toString());

        // Optimistic lock check
        showRepository.store(show);

        order.addTickets(generatedTickets);
        for (Ticket t : generatedTickets) ticketRepository.store(t);
        activeOrderRepository.store(order);

        log.info("Successfully added {} ticket(s) to Order ID: {}", generatedTickets.size(), orderId);
        return ActiveOrderDTO.fromEntity(order);
    }

    @Transactional
    public boolean removeTicketFromOrder(String orderId, String ticketId) throws Exception {
        log.info("Initiating ticket removal for Order ID: {} and Ticket ID: {}", orderId, ticketId);

        ActiveOrder order = activeOrderRepository.findById(orderId);
        if (order == null) {
            log.warn("Ticket removal aborted: Order ID {} not found", orderId);
            throw new Exception("Order not found");
        }

        if (!order.containsTicket(ticketId)) {
            log.warn("Ticket removal aborted: Ticket ID {} not found in Order ID: {}", ticketId, orderId);
            throw new Exception("Ticket not found in the active order");
        }

        Ticket ticket = ticketRepository.findById(ticketId);
        if (ticket == null) {
            log.warn("Ticket removal aborted: Ticket ID {} not found in repository", ticketId);
            throw new Exception("Ticket not found");
        }

        Show show = showRepository.findById(order.getShowId());
        if (show == null) {
            log.warn("Ticket removal aborted: Show ID {} not found", order.getShowId());
            throw new Exception("Show not found");
        }

        boolean removed = order.removeTicket(ticketId, ticket.getPrice());
        if (!removed) {
            log.warn("Ticket removal aborted: Could not remove Ticket ID {} from Order ID {}", ticketId, orderId);
            throw new Exception("Failed to remove ticket from order");
        }

        show.releaseTicket(ticket);

        showRepository.store(show);
        activeOrderRepository.store(order);
        ticketRepository.delete(ticketId);

        log.info("Successfully removed Ticket ID {} from Order ID {} and released back to inventory", ticketId,
                orderId);
        return true;
    }

    private Member validateTokenAndGetMember(String token) throws UnauthorizedException {
        if (token == null || token.isEmpty()) {
            return null;
        }
        if (!authService.validateToken(token)) {
            log.error("Ticket reservation failed: Invalid token provided");
            throw new UnauthorizedException("Invalid token provided.");
        }
        String memberEmail = authService.extractEmailFromToken(token);
        return memberRepository.findByEmail(memberEmail);
    }

    private void validateMemberHasNoActiveOrder(Member member) throws Exception {
        if (member != null && member.hasActiveOrder()) {
            ActiveOrder existingOrder = activeOrderRepository.findById(member.getActiveOrderId());
            if (existingOrder != null && !existingOrder.isExpired(java.time.LocalDateTime.now())) {
                log.warn("Reservation aborted: Member already has an active order");
                throw new Exception("Member already has an active order");
            }
        }
    }

    private Show loadShowOrThrow(String showId) throws Exception {
        Show show = showRepository.findById(showId);
        if (show == null) {
            log.warn("Reservation aborted: Show ID {} not found", showId);
            throw new Exception("Show not found");
        }
        return show;
    }

    private Event loadEventOrThrow(String eventId) throws Exception {
        Event event = eventRepository.findById(eventId);
        if (event == null) {
            log.warn("Reservation aborted: Event ID {} not found", eventId);
            throw new Exception("Event not found");
        }
        return event;
    }

    private String resolveQueueIdentifier(Member member, String guestId) throws Exception {
        String identifier = (member != null) ? member.getEmail() : guestId;
        if (identifier == null || identifier.isEmpty()) {
            log.warn("Reservation aborted: no token and no guest id provided");
            throw new Exception("User identification is required to reserve tickets.");
        }
        return identifier;
    }
}
