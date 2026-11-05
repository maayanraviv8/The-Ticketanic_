package acceptance;

import application.dto.VenueMapDTO;
import application.dto.ZoneDTO;
import application.factories.DiscountPolicyFactory;
import application.factories.PurchasePolicyFactory;
import domain.Aggregates.activeorder.ActiveOrder;
import domain.Aggregates.admin.Admin;
import domain.Aggregates.event.Event;
import domain.Aggregates.event.EventType;
import domain.Aggregates.event.Raffle;
import domain.Aggregates.event.RaffleAuthorization;
import domain.Aggregates.event.RaffleStatus;
import domain.Aggregates.member.Member;
import domain.Aggregates.notification.Notification;
import domain.Aggregates.order.Order;
import domain.Aggregates.productioncompany.ManagerRole;
import domain.Aggregates.productioncompany.OwnerRole;
import domain.Aggregates.productioncompany.Permissions;
import domain.Aggregates.productioncompany.ProductionCompany;
import domain.Aggregates.show.Seat;
import domain.Aggregates.show.Show;
import domain.Aggregates.show.VenueMap;
import domain.Aggregates.show.Zone;
import domain.Aggregates.ticket.Ticket;
import domain.Aggregates.virtualqueue.VirtualQueue;
import domain.Irepository.IActiveOrderRepository;
import domain.Irepository.IAdminRepository;
import domain.Irepository.IEventRepository;
import domain.Irepository.IMemberRepository;
import domain.Irepository.INotificationRepository;
import domain.Irepository.IOrderRepository;
import application.interfaces.INotificationService;
import domain.Irepository.IProductionCompanyRepository;
import domain.Irepository.IShowRepository;
import domain.Irepository.ITicketRepository;
import domain.Irepository.IVirtualQueueRepository;
import org.junit.jupiter.api.BeforeEach;

import application.dto.EventDTO;
import application.dto.*;
import application.interfaces.IAuth;
import application.dto.PaymentDetailsDTO;
import application.dto.TicketIssuanceRequestDTO;
import application.interfaces.INotifier;
import application.interfaces.IPasswordEncoder;
import application.interfaces.IPaymentGateway;
import application.interfaces.ITicketIssuanceService;
import application.service.NotificationManager;
import acceptance.config.AcceptanceTestConfig;
import infra.config.ActiveOrderProperties;
import infra.config.OrderProperties;
import infra.config.QueueProperties;
import domain.Aggregates.order.CustomerDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import repository.memoryRepo.ActiveOrderMemoryRepo;
import repository.memoryRepo.AdminMemoryRepo;
import repository.memoryRepo.EventMemoryRepo;
import repository.memoryRepo.MemberMemoryRepo;
import repository.memoryRepo.NotificationMemoryRepo;
import repository.memoryRepo.OrderMemoryRepo;
import repository.memoryRepo.ProductionCompanyMemoryRepo;
import repository.memoryRepo.ShowMemoryRepo;
import repository.memoryRepo.TicketMemoryRepo;
import repository.memoryRepo.VirtualQueueMemoryRepo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared consts, repos, and helper methods for all acceptance tests.
 *
 * Brings up a minimal Spring context (see {@link AcceptanceTestConfig}) so that
 * every value in {@code application-test.yml} flows through Spring's
 * {@code @ConfigurationProperties} binding instead of any hand-rolled YAML
 * loader. No Vaadin/Hilla, no auto-config, no JPA — just property binding.
 */
@SpringJUnitConfig(AcceptanceTestConfig.class)
public abstract class AcceptanceTestBase {

    // ─── Consts ─── //

    // Member consts
    protected static final String VALID_USERNAME = "user123";
    protected static final String VALID_EMAIL = "user123@mail.com";
    protected static final String VALID_PASSWORD = "Password1!";

    protected static final String INVALID_EMAIL = "nobody@mail.com";
    protected static final String INVALID_PASSWORD = "wrong";

    protected static final String VALID_USERNAME_2 = "User1";
    protected static final String VALID_EMAIL_2 = "user1@mail.com";

    protected static final String VALID_USERNAME_3 = "User2";
    protected static final String VALID_EMAIL_3 = "user2@mail.com";

    protected static final String OWNER1_USERNAME = "owner1";
    protected static final String OWNER1_EMAIL    = "owner1@mail.com";

    protected static final String OWNER2_USERNAME = "owner2";
    protected static final String OWNER2_EMAIL    = "owner2@mail.com";

    protected static final String MANAGER1_USERNAME = "manager1";
    protected static final String MANAGER1_EMAIL    = "manager1@mail.com";

    protected static final String OWNER_A_USERNAME = "ownerA";
    protected static final String OWNER_A_EMAIL    = "ownerA@mail.com";

    protected static final String MANAGER_B_USERNAME = "managerB";
    protected static final String MANAGER_B_EMAIL    = "managerB@mail.com";

    protected static final String VALID_NEW_USERNAME = "newUser456";
    protected static final String VALID_NEW_PASSWORD = "NewPassword2@";

    // Domain consts
    protected static final String VALID_COMPANY_NAME   = "company123";
    protected static final String VALID_COMPANY_NAME_2 = "companyA";
    protected static final String VALID_COMPANY_NAME_3 = "companyB";
    protected static final String NON_EXISTENT_COMPANY_NAME = "non-existent-company";
    protected static final String VALID_EVENT_ID = "event123";
    protected static final String VALID_EVENT_ID_2 = "event456";
    protected static final String VALID_SHOW_ID = "show123";
    protected static final String VALID_ZONE_ID = "zone1";
    protected static final String VALID_ZONE_NAME = "VIP Balcony";
    protected static final String VALID_SEAT_ID = "seat1";
    protected static final String ACTIVE_ORDER_ID_1 = "activeOrder1";
    protected static final String ACTIVE_ORDER_ID_2 = "activeOrder2";
    protected static final String ORDER_ID_1 = "order1";
    protected static final String ORDER_ID_2 = "order2";
    protected static final String TICKET_ID_1 = "ticket1";
    protected static final String VALID_RAFFLE_CODE = "ABC123";
    protected static final String INVALID_RAFFLE_CODE = "WRONG";

    // Checkout consts
    protected static final CustomerDetails VALID_CUSTOMER_DETAILS = CustomerDetails.builder()
            .name("John Doe").email("john@mail.com").phone("0501234567").age(20).purchaseTime(java.time.LocalDateTime.now()).build();
    protected static final CustomerDetails UNDERAGE_CUSTOMER_DETAILS = CustomerDetails.builder()
            .name("Jane Doe Minor").email("jane_minor@mail.com").phone("0507654321").age(16).build();
    protected static final CustomerDetails CUSTOMER_WITH_3_TICKETS = CustomerDetails.builder()
                    .name("Big Buyer").email("buyer@mail.com").phone("0501234567").age(30).ticketQuantity(3).build();
    protected static final CustomerDetails CUSTOMER_WITH_COUPON = CustomerDetails.builder()
            .name("Big Buyer").email("buyer@mail.com").phone("0501234567").age(30).couponCode("SUMMER15").build();
    // Sandbox card / payer details used only in tests (the real ones come from the checkout form).
    protected static final PaymentDetailsDTO VALID_PAYMENT_DETAILS = new PaymentDetailsDTO(
            "2222333344445555", "4", "2030", "Ticketanic", "262", "USD", "20444444");
    protected static final double VALID_TICKET_PRICE = 100.0;
    protected static final String VALID_PURCHASE_NOTIFICATION_CONTENT = "purchased successfully";
    protected static final int EXPIRED_LOCK_MINUTES = -1;

    protected static final int LOCK_ONE_MINUTE = 1;
    protected static final int DEFAULT_LOCK_MINUTES = 30;

    // Event consts
    protected static final String VALID_EVENT_TYPE_NAME = "Test Event Type";
    protected static final String VALID_ARTIST_NAME = "Test Artist";
    protected static final String VALID_LOCATION = "Test Location";
    protected static final double VALID_MIN_PRICE = 100.0;
    protected static final double VALID_MAX_PRICE = 200.0;
    protected static final EventType VALID_EVENT_TYPE = new EventType(VALID_EVENT_TYPE_NAME, "");
    protected static final int VALID_TOTAL_CAPACITY = 100;
    protected static final LocalDateTime VALID_START_DATE = LocalDateTime.now().minusDays(1);
    protected static final LocalDateTime VALID_END_DATE = LocalDateTime.now().plusDays(1);
    protected static final String NON_MATCHING_KEYWORD = "non-matching-keyword";
    protected static final String NON_MATCHING_CATEGORY = "non-matching-category";
    protected static final String NON_MATCHING_LOCATION = "non-matching-location";
    protected static final String NON_EXISTENT_EVENT_ID = "non-existent-event";
    protected static final String NON_EXISTENT_SHOW_ID = "non-existent-show";
    protected static final double NON_MATCHING_MIN_PRICE = 1000.0;
    protected static final double NON_MATCHING_MAX_PRICE = 2000.0;

    protected static final Set<Permissions> MANAGER_DEFAULT_PERMISSIONS = EnumSet.of(Permissions.VIEW_MANAGEMENT_HIERARCHY);
    protected static final Set<Permissions> EVENT_EDITING_PERMISSIONS = EnumSet.of(Permissions.EVENT_EDITING);

    // ─── Policy Consts ─── //
    protected static final PurchasePolicyDTO MIN_AGE_18_POLICY = PurchasePolicyDTO.builder()
            .type("MIN_AGE").minAge(18).build();

    protected static final PurchasePolicyDTO MAX_TICKETS_2_POLICY = PurchasePolicyDTO.builder()
            .type("MAX_TICKETS").maxTickets(2).build();

    protected static final PurchasePolicyDTO MIN_TICKETS_2_POLICY = PurchasePolicyDTO.builder()
            .type("MIN_TICKETS").maxTickets(5).build();

    protected static final PurchasePolicyDTO OR_POLICY = PurchasePolicyDTO.builder()
            .type("OR").children(List.of(MAX_TICKETS_2_POLICY, MIN_AGE_18_POLICY)).build();

    protected static final PurchasePolicyDTO AND_POLICY = PurchasePolicyDTO.builder()
            .type("AND").children(List.of(MIN_TICKETS_2_POLICY, MIN_AGE_18_POLICY)).build();

    protected static final DiscountPolicyDTO DISCOUNT_SIMPLE_10 = DiscountPolicyDTO.builder()
            .type("SIMPLE").percentage(10.0).build();

    protected static final DiscountPolicyDTO DISCOUNT_SIMPLE_20 = DiscountPolicyDTO.builder()
            .type("SIMPLE").percentage(20.0).build();

    protected static final DiscountPolicyDTO DISCOUNT_COUPON_15 = DiscountPolicyDTO.builder()
            .type("COUPON").couponCode("SUMMER15").percentage(15.0).validUntil(java.time.LocalDateTime.now().plusDays(5)).build();

    protected static final DiscountPolicyDTO DISCOUNT_CONDITIONAL_MAX_2 = DiscountPolicyDTO.builder()
            .type("CONDITIONAL_TICKETS").percentage(5.0).maxTickets(2).build();

    protected static final DiscountPolicyDTO DISCOUNT_TIME_VALID = DiscountPolicyDTO.builder()
            .type("CONDITIONAL_TICKETS").percentage(15.0).validFrom(java.time.LocalDateTime.now().minusYears(1))
            .validUntil(java.time.LocalDateTime.now().plusYears(1)).minTickets(null)
            .maxTickets(null).build();

    protected static final DiscountPolicyDTO DISCOUNT_TIME_EXPIRED = DiscountPolicyDTO.builder()
            .type("CONDITIONAL_TICKETS").percentage(15.0).validFrom(java.time.LocalDateTime.now().minusDays(7))
            .validUntil(java.time.LocalDateTime.now().minusDays(2)).build();

    protected static final application.dto.DiscountPolicyDTO DISCOUNT_COMPOSITE_MAX = DiscountPolicyDTO.builder()
            .type("MAX").children(List.of(DISCOUNT_SIMPLE_10, DISCOUNT_SIMPLE_20)).build();

    protected static final DiscountPolicyDTO DISCOUNT_COMPOSITE_SUM = DiscountPolicyDTO.builder()
            .type("SUM").children(List.of(DISCOUNT_SIMPLE_10, DISCOUNT_SIMPLE_20)).build();


    // ─── Repositories ─── //

    protected IMemberRepository memberRepo;
    protected IActiveOrderRepository activeOrderRepo;
    protected IOrderRepository orderRepo;
    protected ITicketRepository ticketRepo;
    protected IShowRepository showRepo;
    protected IEventRepository eventRepo;
    protected IProductionCompanyRepository companyRepo;
    protected IAdminRepository adminRepo;
    protected INotificationRepository notificationRepo;
    protected IVirtualQueueRepository virtualQueueRepo;

    // External dependencies
    protected IAuth authService;
    protected IPasswordEncoder passwordEncoder;
    protected IPaymentGateway paymentGateway;
    protected ITicketIssuanceService ticketIssuanceService;
    protected INotificationService notificationService;
    protected INotifier notifier;
    protected DiscountPolicyFactory discountPolicyFactory;
    protected PurchasePolicyFactory purchasePolicyFactory;

    // Spring-bound from application-test.yml so tests follow external config
    // for queue size, lock duration, retries, persistence backend, etc.
    @Autowired protected QueueProperties TEST_QUEUE_PROPERTIES;
    @Autowired protected ActiveOrderProperties TEST_ACTIVE_ORDER_PROPERTIES;
    @Autowired protected OrderProperties TEST_ORDER_PROPERTIES;
    @Value("${ticketanic.persistence.mode}") private String persistenceMode;

    @BeforeEach
    void baseSetUp() {
        wireRepositories();

        notifier = new StubNotifier();
        //Notification service with stub external notifier and real repository for testing
        notificationService = new NotificationManager(notificationRepo, notifier);

        authService = new StubAuth();
        passwordEncoder = new StubPasswordEncoder();
        paymentGateway = new StubPaymentGateway();
        ticketIssuanceService = new StubTicketIssuanceService();
        discountPolicyFactory = new DiscountPolicyFactory();
        purchasePolicyFactory = new PurchasePolicyFactory();
    }

    /**
     * Picks the repository backend based on {@code ticketanic.persistence.mode}.
     * Only {@code memory} is wired today.
     */
    private void wireRepositories() {
        switch (persistenceMode == null ? "memory" : persistenceMode.toLowerCase()) {
            case "memory" -> {
                memberRepo = new MemberMemoryRepo();
                activeOrderRepo = new ActiveOrderMemoryRepo();
                orderRepo = new OrderMemoryRepo();
                ticketRepo = new TicketMemoryRepo();
                showRepo = new ShowMemoryRepo();
                eventRepo = new EventMemoryRepo();
                companyRepo = new ProductionCompanyMemoryRepo();
                adminRepo = new AdminMemoryRepo();
                notificationRepo = new NotificationMemoryRepo();
                virtualQueueRepo = new VirtualQueueMemoryRepo(TEST_QUEUE_PROPERTIES);
            }
            default -> throw new IllegalStateException(
                    "Unknown ticketanic.persistence.mode='" + persistenceMode + "' (allowed: memory)");
        }
    }

    // ─── Member Helpers ─── //

    protected void storeMember(String username, String email, String password) {
        String hashedPassword = passwordEncoder.encode(password);
        memberRepo.store(new Member(username, email, hashedPassword));
    }

    protected String storeMemberAndLogin(String username, String email, String password) {
        String hashedPassword = passwordEncoder.encode(password);
        Member member = new Member(username, email, hashedPassword);
        memberRepo.store(member);
        // Login state is session-scoped (JWT + Vaadin session); possession of a
        // valid token is the session, so simply minting one is enough here.
        return authService.generateToken(email);
    }

    protected void linkOrderToMember(String email, String orderId) {
        Member member = memberRepo.findByEmail(email);
        List<String> ids = member.getOrderIds() != null
                ? new ArrayList<>(member.getOrderIds())
                : new ArrayList<>();
        ids.add(orderId);
        member.setOrderIds(ids);
        memberRepo.store(member);
    }

    // ─── Ticket Helpers ─── //

    protected Ticket storeTicket(String id, double price) {
        Ticket ticket = new Ticket(id, VALID_SHOW_ID, VALID_ZONE_ID, VALID_SEAT_ID, price);
        ticketRepo.store(ticket);
        return ticket;
    }

    // ─── ActiveOrder Helpers ─── //

    protected ActiveOrder storeActiveOrder(String id, String eventId, String showId, int lockDurationMinutes) {
        ActiveOrder order = new ActiveOrder(id, eventId, showId, lockDurationMinutes);
        activeOrderRepo.store(order);
        return order;
    }

    protected ActiveOrder storeActiveOrderWithTicket(String id, String eventId,
            String showId, Ticket ticket, int lockDurationMinutes) {
        ActiveOrder order = new ActiveOrder(id, eventId, showId, lockDurationMinutes);
        order.addTicket(ticket.getId(), ticket.getPrice());
        activeOrderRepo.store(order);
        return order;
    }

    protected void linkActiveOrderToMember(String email, String activeOrderId) {
        Member member = memberRepo.findByEmail(email);
        member.assignActiveOrder(activeOrderId);
        memberRepo.store(member);
    }

    // ─── Order Helpers ─── //

    protected Order storeOrder(String orderId, ActiveOrder activeOrder) {
        Order order = new Order(
                orderId,
                activeOrder.getEventId(),
                activeOrder.getShowId(),
                List.copyOf(activeOrder.getTicketIds()),
                activeOrder.getTotalPrice(),
                LocalDateTime.now(),
                null);
        orderRepo.store(order);
        return order;
    }


    // ─── Show / Event / Company Helpers ─── //

    protected Show storeShow(String eventId, boolean active) {
        return storeShow(eventId, active, 0);
    }

    protected Show storeShow(String eventId, boolean active, int totalCapacity) {
        Show show = Show.builder().eventId(eventId).active(active).totalCapacity(totalCapacity).build();
        showRepo.store(show);
        return show;
    }

    protected VenueMapDTO createSeatedVenueMapDTO(String showId, int rows, int seatsPerRow, double basePrice) {
        ZoneDTO zone = new ZoneDTO();
        zone.standing = false;
        zone.rows = rows;
        zone.seatsPerRow = seatsPerRow;
        zone.basePrice = basePrice;

        VenueMapDTO dto = new VenueMapDTO();
        dto.showId = showId;
        dto.mapName = "Main Hall";
        dto.totalCapacity = rows * seatsPerRow;
        dto.zoneCount = 1;
        dto.zones = List.of(zone);
        return dto;
    }

    protected Show storeShowWithStandingZone(String eventId, String zoneId, int capacity) {
        Zone zone = Zone.builder().zoneId(zoneId).standing(true)
                .standingCapacity(capacity).standingAvailable(capacity).build();
        VenueMap venueMap = VenueMap.builder().build();
        venueMap.addZone(zone);
        Show show = Show.builder().eventId(eventId).active(true).build();
        showRepo.store(show);
        venueMap.setShowId(show.getShowId());
        show.setVenueMap(venueMap);
        showRepo.store(show);
        return show;
    }

    protected Show storeShowWithSeat(String eventId, String zoneId, String seatId) {
        Seat seat = Seat.builder().id(seatId).row(1).number(1).price(50.0).build();
        Zone zone = Zone.builder().zoneId(zoneId).standing(false).build();
        zone.addSeat(seat);
        Show show = Show.builder().eventId(eventId).active(true).build();
        showRepo.store(show);
        VenueMap venueMap = VenueMap.builder().showId(show.getShowId()).build();
        venueMap.addZone(zone);
        show.setVenueMap(venueMap);
        showRepo.store(show);
        return show;
    }

    /**
     * Stores a show with a single named seated zone holding one seat (row {@code row},
     * number {@code number}). Returns the stored Show so callers can use its generated id.
     */
    protected Show storeSeatedShow(String eventId, String zoneId, String zoneName,
            String seatId, int row, int number, double price) {
        Seat seat = Seat.builder().id(seatId).row(row).number(number).price(price).build();
        Zone zone = Zone.builder().zoneId(zoneId).zoneName(zoneName).standing(false).build();
        zone.addSeat(seat);
        Show show = Show.builder().eventId(eventId).active(true).build();
        showRepo.store(show);
        VenueMap venueMap = VenueMap.builder().showId(show.getShowId()).build();
        venueMap.addZone(zone);
        show.setVenueMap(venueMap);
        showRepo.store(show);
        return show;
    }

    protected Event storeEvent(String companyId, boolean active) {
        return storeEventWithManager(companyId, active, VALID_EVENT_ID, VALID_EMAIL);
    }

    protected Event storeEventWithManager(String companyId, boolean active, String eventName, String managerId) {
        Event event = Event.builder()
                .productionCompanyName(companyId)
                .managerId(managerId)
                .active(active)
                .startDate(VALID_START_DATE)
                .endDate(VALID_END_DATE)
                .name(eventName)
                .eventType(VALID_EVENT_TYPE)
                .artist(VALID_ARTIST_NAME)
                .location(VALID_LOCATION)
                .minPrice(VALID_MIN_PRICE)
                .maxPrice(VALID_MAX_PRICE)
                .build();
        eventRepo.store(event);
        return event;
    }

    /** Event with an OPEN raffle whose registration deadline is in the future (sale not open yet). */
    protected Event storeEventWithRaffle(String companyId, boolean active) {
        Raffle raffle = Raffle.builder()
                .raffleId("raffle-1")
                .registrationDeadline(LocalDateTime.now().plusDays(1))
                .codeValidity(Duration.ofMinutes(30))
                .numberOfWinners(VALID_TOTAL_CAPACITY)
                .status(RaffleStatus.OPEN)
                .build();
        Event event = Event.builder()
                .productionCompanyName(companyId)
                .active(active)
                .raffle(raffle)
                .build();
        eventRepo.store(event);
        return event;
    }

    /** Event with an already-DRAWN raffle where {@code winnerEmail} holds {@code code}, valid for 30 min. */
    protected Event storeEventWithDrawnRaffle(String companyId, boolean active, String winnerEmail, String code) {
        Map<String, RaffleAuthorization> winners = new HashMap<>();
        winners.put(winnerEmail, new RaffleAuthorization(code, winnerEmail, LocalDateTime.now().plusMinutes(30)));
        Raffle raffle = Raffle.builder()
                .raffleId("raffle-1")
                .registrationDeadline(LocalDateTime.now().minusMinutes(5))
                .codeValidity(Duration.ofMinutes(30))
                .numberOfWinners(VALID_TOTAL_CAPACITY)
                .status(RaffleStatus.DRAWN)
                .winners(winners)
                .build();
        Event event = Event.builder()
                .productionCompanyName(companyId)
                .active(active)
                .raffle(raffle)
                .build();
        eventRepo.store(event);
        return event;
    }

    protected ProductionCompany storeCompany(String name, String founderEmail) {
        ProductionCompany company = new ProductionCompany(name, founderEmail); //The constructor creates the founder role
        companyRepo.store(company);
        return company;
    }

    protected void storeOwnerInCompany(String companyName, String founderEmail, String ownerEmail) {
        ProductionCompany company = companyRepo.findById(companyName);
        company.getRoles().put(ownerEmail, new OwnerRole(ownerEmail, founderEmail));
        companyRepo.store(company);
    }

    protected void storeManagerInCompany(String companyName, String founderEmail, String managerEmail) {
        // Create manager role with default permissions (VIEW_MANAGEMENT_HIERARCHY)
        ProductionCompany company = companyRepo.findById(companyName);
        company.getRoles().put(managerEmail, new ManagerRole(managerEmail, founderEmail, MANAGER_DEFAULT_PERMISSIONS));
        companyRepo.store(company);
    }

    protected void storeManagerInCompanyWithPermissions(String companyName, String founderEmail, String managerEmail, Set<Permissions> permissions) {
        ProductionCompany company = companyRepo.findById(companyName);
        company.getRoles().put(managerEmail, new ManagerRole(managerEmail, founderEmail, permissions));
        companyRepo.store(company);
    }

    protected void sendManagerAppointmentNotification(String companyID, String appointerID, String targetID, Set<Permissions> permissions) {
        notificationService.sendAppointment(targetID, companyID, appointerID, "Appointment Request", permissions);
    }

    protected EventDTO createValidEventDTO() {
        EventDTO eventDTO = new EventDTO();
        eventDTO.name = VALID_EVENT_ID;
        eventDTO.eventTypeName = VALID_EVENT_TYPE_NAME;
        eventDTO.startDate = VALID_START_DATE;
        eventDTO.endDate = VALID_END_DATE;
        return eventDTO;
    }

    // ─── Admin Helpers ─── //

    protected void storeAdmin(String email) {
        adminRepo.store(new Admin(email));
    }

    // ─── Purchase Chain Helpers ─── //

    protected Order storeFullPurchaseChain(String buyerEmail, String activeOrderId,
            String orderId, String eventId, String showId, double finalPrice,
            LocalDateTime purchaseTime) {
        ActiveOrder activeOrder = new ActiveOrder(activeOrderId, eventId, showId, DEFAULT_LOCK_MINUTES);
        activeOrderRepo.store(activeOrder);
        Order order = new Order(orderId, eventId, showId, List.of(), finalPrice, purchaseTime, null);
        orderRepo.store(order);
        linkOrderToMember(buyerEmail, orderId);
        return order;
    }

    // ─── Sales Report Helpers ─── //

    /**
     * Persists a finalized {@link Order} for a given event with the requested number of ticket
     * ids and total price.
     */
    protected Order storeOrderForEvent(String orderId, String eventId, int ticketCount, double price) {
        List<String> ticketIds = new ArrayList<>();
        for (int i = 0; i < ticketCount; i++) {
            ticketIds.add(orderId + "-ticket-" + i);
        }
        Order order = new Order(orderId, eventId, VALID_SHOW_ID, ticketIds, price, LocalDateTime.now(), VALID_CUSTOMER_DETAILS);
        orderRepo.store(order);
        Event event = eventRepo.findById(eventId);
        if (event != null) {
            event.recordSale(ticketCount, price);
            eventRepo.store(event);
        }
        return order;
    }

    // ─── Stub Password Encoder ─── //

    private static class StubPasswordEncoder implements IPasswordEncoder {
        @Override
        public String encode(String rawPassword) {
            return "encoded_" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return encodedPassword.equals("encoded_" + rawPassword);
        }
    }

    // ─── Stub Payment Gateway ─── //

    protected static class StubPaymentGateway implements IPaymentGateway {
        @Override
        public String processPayment(String activeOrderId, double amount, PaymentDetailsDTO paymentDetails) {
            // Echo the active-order id as the transaction id so the order id stays stable in tests.
            return activeOrderId;
        }

        @Override
        public void refundPayment(String transactionId, double amount) {}
    }

    protected static class StubFailingPaymentGateway implements IPaymentGateway {
        @Override
        public String processPayment(String activeOrderId, double amount, PaymentDetailsDTO paymentDetails) {
            return null;
        }

        @Override
        public void refundPayment(String transactionId, double amount) {}
    }

    /**
     * Payment gateway that succeeds on charge and records each refund call, so tests can assert
     * that a refund was actually triggered (the plain stub's no-op refund can't be verified).
     */
    protected static class RecordingPaymentGateway implements IPaymentGateway {
        protected int refundCount = 0;
        protected String lastRefundTransactionId;
        protected double lastRefundAmount;

        @Override
        public String processPayment(String activeOrderId, double amount, PaymentDetailsDTO paymentDetails) {
            return activeOrderId; // stable transaction/order id, like StubPaymentGateway
        }

        @Override
        public void refundPayment(String transactionId, double amount) {
            refundCount++;
            lastRefundTransactionId = transactionId;
            lastRefundAmount = amount;
        }
    }

    // ─── Stub Ticket Issuance Service ─── //

    protected static class StubTicketIssuanceService implements ITicketIssuanceService {
        @Override
        public String issueTickets(String activeOrderId, TicketIssuanceRequestDTO request) {
            return "STUB-" + activeOrderId;
        }

        @Override
        public boolean cancelIssuedTickets(String externalTicketCode) {
            return true;
        }
    }

    protected static class StubFailingTicketIssuanceService implements ITicketIssuanceService {
        @Override
        public String issueTickets(String activeOrderId, TicketIssuanceRequestDTO request) {
            return null;
        }

        @Override
        public boolean cancelIssuedTickets(String externalTicketCode) {
            return true;
        }
    }
    // ─── Stub Notifier ─── //

    /**
     * Stub adapter for the external {@link INotifier} port.
     * Always reports "no live channel" so that {@link NotificationManager} queues every
     * notification in the repository, giving acceptance tests a deterministic place to
     * inspect what was sent (via {@code notificationRepo}). The live-delivery branch of
     * NotificationManager is covered by dedicated unit tests.
     */
    public static class StubNotifier implements INotifier {
        @Override
        public boolean notifyUser(String memberId, Notification notification) {
            return false;
        }

        @Override
        public void register(String memberId) {}

        @Override
        public void unregister(String memberId) {}

        @Override
        public void setAppointmentHandler(
                java.util.function.BiConsumer<domain.Aggregates.notification.AppointmentNotification, Boolean> handler) {}
    }

    // ─── Stub Auth ─── //

    private static class StubAuth implements IAuth {
        @Override
        public String generateToken(String email) {
            return email;
        }

        @Override
        public boolean validateToken(String token) {
            return true;
        }

        @Override
        public String extractEmailFromToken(String token) {
            return token;
        }
    }

    // ─── Suspension Helpers ─── //

    protected void suspendMember(String email) {
        Member member = memberRepo.findByEmail(email);
        member.suspend();
        memberRepo.store(member);
    }

    protected void unsuspendMember(String email) {
        Member member = memberRepo.findByEmail(email);
        member.unsuspend();
        memberRepo.store(member);
    }

    // ─── VirtualQueue Helpers ─── //
    protected void grantQueueAccess(String showId, String email) {
        VirtualQueue queue = virtualQueueRepo.findById(showId);
        queue.joinQueue(email);
        virtualQueueRepo.store(queue);
    }
    // ─── Policy Test Helpers ─── //

    protected void setEventPurchasePolicy(String eventId, application.dto.PurchasePolicyDTO policyDTO) {
        Event event = eventRepo.findById(eventId);
        if (event != null) {
            event.setPurchasePolicy(purchasePolicyFactory.createFromDTO(policyDTO));
            eventRepo.store(event);
        }
    }

    protected void setEventDiscountPolicy(String eventId, application.dto.DiscountPolicyDTO policyDTO) {
        Event event = eventRepo.findById(eventId);
        if (event != null) {
            event.setDiscountPolicy(discountPolicyFactory.createFromDTO(policyDTO));
            eventRepo.store(event);
        }
    }

    protected void setCompanyPurchasePolicy(String companyName, PurchasePolicyDTO policyDTO) {
        ProductionCompany company = companyRepo.findById(companyName);
        if (company != null) {
            company.setPurchasePolicy(purchasePolicyFactory.createFromDTO(policyDTO));
            companyRepo.store(company);
        }
    }

    protected void setCompanyDiscountPolicy(String companyName, DiscountPolicyDTO policyDTO) {
        ProductionCompany company = companyRepo.findById(companyName);
        if (company != null) {
            company.setDiscountPolicy(discountPolicyFactory.createFromDTO(policyDTO));
            companyRepo.store(company);
        }
    }

    // ─── Failing Repo Stubs (DB resilience testing) ─── //

    protected static class StoreFailingMemberRepo implements domain.Irepository.IMemberRepository {
        private final domain.Irepository.IMemberRepository d;
        public StoreFailingMemberRepo(domain.Irepository.IMemberRepository d) { this.d = d; }
        @Override public void store(domain.Aggregates.member.Member e) { throw new RuntimeException("DB connection lost"); }
        @Override public void delete(String id) { d.delete(id); }
        @Override public domain.Aggregates.member.Member findById(String id) { return d.findById(id); }
        @Override public java.util.List<domain.Aggregates.member.Member> getAll() { return d.getAll(); }
        @Override public domain.Aggregates.member.Member findByEmail(String email) { return d.findByEmail(email); }
        @Override public domain.Aggregates.member.Member findByUsername(String u) { return d.findByUsername(u); }
        @Override public domain.Aggregates.member.Member findByActiveOrderId(String id) { return d.findByActiveOrderId(id); }
    }

    protected static class StoreFailingCompanyRepo implements domain.Irepository.IProductionCompanyRepository {
        private final domain.Irepository.IProductionCompanyRepository d;
        public StoreFailingCompanyRepo(domain.Irepository.IProductionCompanyRepository d) { this.d = d; }
        @Override public void store(ProductionCompany e) { throw new RuntimeException("DB connection lost"); }
        @Override public void delete(String id) { d.delete(id); }
        @Override public ProductionCompany findById(String id) { return d.findById(id); }
        @Override public java.util.List<ProductionCompany> getAll() { return d.getAll(); }
        @Override public boolean isActive(String id) { return d.isActive(id); }
    }

    protected static class StoreFailingEventRepo extends DelegatingEventRepo {
        public StoreFailingEventRepo(domain.Irepository.IEventRepository d) { super(d); }
        @Override public void store(Event e) { throw new RuntimeException("DB connection lost"); }
    }

    protected static class DeleteFailingEventRepo extends DelegatingEventRepo {
        public DeleteFailingEventRepo(domain.Irepository.IEventRepository d) { super(d); }
        @Override public void delete(String id) { throw new RuntimeException("DB connection lost"); }
    }

    protected static class StoreFailingShowRepo extends DelegatingShowRepo {
        public StoreFailingShowRepo(domain.Irepository.IShowRepository d) { super(d); }
        @Override public void store(domain.Aggregates.show.Show e) { throw new RuntimeException("DB connection lost"); }
    }

    protected static class VenueMapSaveFailingShowRepo extends DelegatingShowRepo {
        public VenueMapSaveFailingShowRepo(domain.Irepository.IShowRepository d) { super(d); }
        @Override public boolean saveVenueMapLayout(String showId, Object data) { throw new RuntimeException("DB connection lost"); }
    }

    protected static class VenueMapUpdateFailingShowRepo extends DelegatingShowRepo {
        public VenueMapUpdateFailingShowRepo(domain.Irepository.IShowRepository d) { super(d); }
        @Override public boolean updateVenueMapLayout(String showId, Object data) { throw new RuntimeException("DB connection lost"); }
    }

    protected static class VenueMapDeleteFailingShowRepo extends DelegatingShowRepo {
        public VenueMapDeleteFailingShowRepo(domain.Irepository.IShowRepository d) { super(d); }
        @Override public boolean deleteVenueMap(String showId) { throw new RuntimeException("DB connection lost"); }
    }

    protected static class DelegatingEventRepo implements domain.Irepository.IEventRepository {
        protected final domain.Irepository.IEventRepository d;
        public DelegatingEventRepo(domain.Irepository.IEventRepository d) { this.d = d; }
        @Override public void store(Event e) { d.store(e); }
        @Override public void delete(String id) { d.delete(id); }
        @Override public Event findById(String id) { return d.findById(id); }
        @Override public java.util.List<Event> getAll() { return d.getAll(); }
        @Override public java.util.List<Event> findByProductionCompanyId(String id) { return d.findByProductionCompanyId(id); }
        @Override public java.util.List<Event> findByEventType(String t) { return d.findByEventType(t); }
        @Override public boolean existsByNameAndProductionCompany(String n, String c) { return d.existsByNameAndProductionCompany(n, c); }
        @Override public java.util.List<Event> findByDateRange(java.time.LocalDateTime s, java.time.LocalDateTime e) { return d.findByDateRange(s, e); }
        @Override public java.util.List<Event> findRaffleEnabledEvents() { return d.findRaffleEnabledEvents(); }
        @Override public boolean updateEventInventory(String id, Event ev) { return d.updateEventInventory(id, ev); }
        @Override public int countByProductionCompanyId(String id) { return d.countByProductionCompanyId(id); }
        @Override public java.util.List<Event> findByManagerId(String id) { return d.findByManagerId(id); }
        @Override public java.util.List<Event> searchEvents(String c, java.util.List<String> kw, String cat, java.time.LocalDateTime s, java.time.LocalDateTime e, String loc, Double min, Double max) { return d.searchEvents(c, kw, cat, s, e, loc, min, max); }
    }

    protected static class DelegatingShowRepo implements domain.Irepository.IShowRepository {
        protected final domain.Irepository.IShowRepository d;
        public DelegatingShowRepo(domain.Irepository.IShowRepository d) { this.d = d; }
        @Override public void store(domain.Aggregates.show.Show e) { d.store(e); }
        @Override public void delete(String id) { d.delete(id); }
        @Override public domain.Aggregates.show.Show findById(String id) { return d.findById(id); }
        @Override public java.util.List<domain.Aggregates.show.Show> getAll() { return d.getAll(); }
        @Override public java.util.List<domain.Aggregates.show.Show> findByEventId(String id) { return d.findByEventId(id); }
        @Override public java.util.List<domain.Aggregates.show.Show> findByDateRange(java.time.LocalDateTime s, java.time.LocalDateTime e) { return d.findByDateRange(s, e); }
        @Override public boolean existsByEventIdAndDate(String id, java.time.LocalDateTime dt) { return d.existsByEventIdAndDate(id, dt); }
        @Override public java.util.List<domain.Aggregates.show.Show> findByEventIdOrderByDate(String id) { return d.findByEventIdOrderByDate(id); }
        @Override public int getTotalInventoryByShowId(String id) { return d.getTotalInventoryByShowId(id); }
        @Override public boolean updateShowZones(String id, domain.Aggregates.show.Show s) { return d.updateShowZones(id, s); }
        @Override public int countByEventId(String id) { return d.countByEventId(id); }
        @Override public boolean saveVenueMapLayout(String id, Object data) { return d.saveVenueMapLayout(id, data); }
        @Override public domain.Aggregates.show.VenueMap getVenueMapByShowId(String id) { return d.getVenueMapByShowId(id); }
        @Override public boolean validateMapCapacityMatch(String id, Object data) { return d.validateMapCapacityMatch(id, data); }
        @Override public boolean updateVenueMapLayout(String id, Object data) { return d.updateVenueMapLayout(id, data); }
        @Override public boolean deleteVenueMap(String id) { return d.deleteVenueMap(id); }
        @Override public boolean venueMapExists(String id) { return d.venueMapExists(id); }
        @Override public int getTotalMapCapacity(String id) { return d.getTotalMapCapacity(id); }
    }

}
