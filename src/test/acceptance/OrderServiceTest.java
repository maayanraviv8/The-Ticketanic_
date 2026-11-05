package acceptance;
import application.dto.OrderDTO;
import application.dto.OrderPricePreviewDTO;
import application.dto.TicketIssuanceRequestDTO;
import application.interfaces.ITicketIssuanceService;
import application.service.OrderService;
import domain.Aggregates.activeorder.ActiveOrder;
import domain.Aggregates.event.Event;
import domain.Aggregates.member.Member;
import domain.Aggregates.notification.Notification;
import domain.Aggregates.order.Order;
import domain.Aggregates.show.Show;
import domain.Aggregates.ticket.Ticket;
import domain.Irepository.IOrderRepository;
import infra.config.ExternalServicesProperties;
import infra.external.WsepExternalClient;
import infra.ticketing.HttpTicketIssuanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.memoryRepo.OrderMemoryRepo;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OrderServiceTest extends AcceptanceTestBase {

    private OrderService orderService;
    private OrderService failingPaymentService;
    private String eventId;

    @BeforeEach
    void setUp() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        eventId = storeEvent(VALID_COMPANY_NAME, true).getEventId();
        orderService = new OrderService(orderRepo, activeOrderRepo, paymentGateway, ticketIssuanceService, memberRepo, showRepo, ticketRepo, notificationService, eventRepo, companyRepo, TEST_ORDER_PROPERTIES);
        failingPaymentService = new OrderService(orderRepo, activeOrderRepo, new StubFailingPaymentGateway(), ticketIssuanceService, memberRepo, showRepo, ticketRepo, notificationService, eventRepo, companyRepo, TEST_ORDER_PROPERTIES);
    }

    ///////// UC7 - Checkout /////////

    @Test
    void givenValidActiveOrderAndPayment_whenCheckout_thenOrderCreatedAndActiveOrderDeleted() throws Exception {
        // Arrange
        Ticket ticket = storeTicket(TICKET_ID_1, VALID_TICKET_PRICE);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, eventId, VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);

        // Act
        OrderDTO result = orderService.checkout(activeOrder.getId(), VALID_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS);

        // Assert - return value
        assertNotNull(result);
        assertEquals(activeOrder.getId(), result.getOrderId());
        assertEquals(eventId, result.getEventId());
        assertEquals(VALID_SHOW_ID, result.getShowId());
        assertEquals(List.of(TICKET_ID_1), result.getTicketIds());
        assertEquals(VALID_TICKET_PRICE, result.getFinalPrice());

        // Assert - final Order is persisted, with the external ticket code from issuance
        Order stored = orderRepo.findById(result.getOrderId());
        assertNotNull(stored);
        assertEquals("STUB-" + activeOrder.getId(), stored.getExternalTicketCode());

        // Assert - ActiveOrder is consumed and removed
        assertNull(activeOrderRepo.findById(activeOrder.getId()));

        // Assert - guest checkout: no purchase notification was sent
        assertEquals(0, notificationRepo.getAll().size());
    }

    @Test
    void givenValidActiveOrderAndPaymentMember_whenCheckout_thenOrderCreatedAndMemberLinked() throws Exception {
        // Arrange
        Ticket ticket = storeTicket(TICKET_ID_1, VALID_TICKET_PRICE);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, eventId, VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);
        storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        linkActiveOrderToMember(VALID_EMAIL, activeOrder.getId());

        // Act
        OrderDTO result = orderService.checkout(activeOrder.getId(), VALID_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS);

        // Assert - return value
        assertNotNull(result);
        assertEquals(activeOrder.getId(), result.getOrderId());
        assertEquals(eventId, result.getEventId());
        assertEquals(VALID_SHOW_ID, result.getShowId());
        assertEquals(List.of(TICKET_ID_1), result.getTicketIds());
        assertEquals(VALID_TICKET_PRICE, result.getFinalPrice());

        // Assert - final Order is persisted
        assertNotNull(orderRepo.findById(result.getOrderId()));

        // Assert - ActiveOrder is consumed and removed
        assertNull(activeOrderRepo.findById(activeOrder.getId()));

        // Assert - member is linked to the order
        Member member = memberRepo.findByEmail(VALID_EMAIL);
        assertEquals(result.getOrderId(), member.getOrderIds().get(0));

        // Assert - active order is not linked to the member anymore
        assertNull(member.getActiveOrderId());

        // Assert - exactly one purchase notification was sent to the member with the expected content
        List<Notification> sent = notificationRepo.findByRecipientEmail(VALID_EMAIL);
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).getContent().contains(VALID_PURCHASE_NOTIFICATION_CONTENT));
    }

    @Test
    void givenPaymentFails_whenCheckout_thenThrowsAndNoOrderCreated() {
        // Arrange
        Ticket ticket = storeTicket(TICKET_ID_1, VALID_TICKET_PRICE);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, eventId, VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);

        // Act & Assert
        assertThrows(Exception.class, () ->
                failingPaymentService.checkout(activeOrder.getId(), VALID_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS));

        // Assert - no Order was created
        assertEquals(0, orderRepo.getAll().size());

        // Assert - ActiveOrder is still present (not consumed)
        assertNotNull(activeOrderRepo.findById(activeOrder.getId()));
    }

    @Test
    void givenPaymentFailed_whenRetryWithSucceedingPayment_thenOrderCreated() throws Exception {
        // Arrange - one reservation; first checkout uses a failing gateway
        Ticket ticket = storeTicket(TICKET_ID_1, VALID_TICKET_PRICE);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, eventId, VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);

        // Act - payment fails; the reservation must survive so the user can update card details and retry
        assertThrows(Exception.class, () ->
                failingPaymentService.checkout(activeOrder.getId(), VALID_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS));
        assertNotNull(activeOrderRepo.findById(activeOrder.getId()));

        // Act - retry the same active order with succeeding payment (corrected card details)
        OrderDTO result = orderService.checkout(activeOrder.getId(), VALID_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS);

        // Assert - the retry produced exactly one order and consumed the reservation
        assertNotNull(result);
        assertEquals(1, orderRepo.getAll().size());
        assertNull(activeOrderRepo.findById(activeOrder.getId()));
    }

    @Test
    void givenTicketIssuanceFails_whenCheckout_thenThrowsRefundsAndNoOrderCreated() {
        // Arrange - payment succeeds (recorded), issuance fails
        Ticket ticket = storeTicket(TICKET_ID_1, VALID_TICKET_PRICE);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, eventId, VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);
        RecordingPaymentGateway recordingGateway = new RecordingPaymentGateway();
        OrderService service = new OrderService(orderRepo, activeOrderRepo, recordingGateway,
                new StubFailingTicketIssuanceService(), memberRepo, showRepo, ticketRepo,
                notificationService, eventRepo, companyRepo, TEST_ORDER_PROPERTIES);

        // Act & Assert
        assertThrows(Exception.class, () ->
                service.checkout(activeOrder.getId(), VALID_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS));

        // Assert - the payment was refunded for the transaction (the active-order id) and amount
        assertEquals(1, recordingGateway.refundCount);
        assertEquals(activeOrder.getId(), recordingGateway.lastRefundTransactionId);
        assertEquals(VALID_TICKET_PRICE, recordingGateway.lastRefundAmount);

        // Assert - no Order was created
        assertEquals(0, orderRepo.getAll().size());

        // Assert - ActiveOrder is still present (not consumed)
        assertNotNull(activeOrderRepo.findById(activeOrder.getId()));
    }

    @Test
    void givenExternalIssuanceClientFails_whenCheckout_thenRefundsAndThrows() {
        // Arrange - a REAL HttpTicketIssuanceService whose external client reports failure (null),
        // exercising the client -> adapter -> checkout failure translation end to end.
        Ticket ticket = storeTicket(TICKET_ID_1, VALID_TICKET_PRICE);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, eventId, VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);

        WsepExternalClient client = mock(WsepExternalClient.class);
        when(client.issueStandingTicket(anyString(), anyString(), anyString(), anyInt())).thenReturn(null);
        when(client.issueSeatedTickets(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyList())).thenReturn(null);
        ExternalServicesProperties props = new ExternalServicesProperties(
                "http://localhost/", 1000, 5000, null, null, null, null);
        HttpTicketIssuanceService realIssuance = new HttpTicketIssuanceService(client, props);

        RecordingPaymentGateway recordingGateway = new RecordingPaymentGateway();
        OrderService service = new OrderService(orderRepo, activeOrderRepo, recordingGateway, realIssuance,
                memberRepo, showRepo, ticketRepo, notificationService, eventRepo, companyRepo, TEST_ORDER_PROPERTIES);

        // Act & Assert
        assertThrows(Exception.class, () ->
                service.checkout(activeOrder.getId(), VALID_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS));

        // Assert - refund was triggered and no order was created
        assertEquals(1, recordingGateway.refundCount);
        assertEquals(0, orderRepo.getAll().size());
        assertNotNull(activeOrderRepo.findById(activeOrder.getId()));
    }

    @Test
    void givenPostIssuanceStepFails_whenCheckout_thenCancelsIssuedTicketAndRefunds() {
        // Arrange - issuance succeeds, but persisting the Order throws (simulates a later-step failure).
        Ticket ticket = storeTicket(TICKET_ID_1, VALID_TICKET_PRICE);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, eventId, VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);

        RecordingPaymentGateway recordingGateway = new RecordingPaymentGateway();
        RecordingIssuanceService issuance = new RecordingIssuanceService();
        IOrderRepository throwingOrderRepo = new OrderMemoryRepo() {
            @Override
            public void store(Order order) {
                throw new RuntimeException("DB write failed");
            }
        };
        OrderService service = new OrderService(throwingOrderRepo, activeOrderRepo, recordingGateway, issuance,
                memberRepo, showRepo, ticketRepo, notificationService, eventRepo, companyRepo, TEST_ORDER_PROPERTIES);

        // Act & Assert
        assertThrows(Exception.class, () ->
                service.checkout(activeOrder.getId(), VALID_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS));

        // Assert - compensation: the issued ticket was cancelled and the payment refunded
        assertEquals(1, issuance.cancelCount);
        assertEquals(issuance.code, issuance.cancelledCode);
        assertEquals(1, recordingGateway.refundCount);
        assertEquals(activeOrder.getId(), recordingGateway.lastRefundTransactionId);

        // Assert - no Order persisted, ActiveOrder still present
        assertEquals(0, orderRepo.getAll().size());
        assertNotNull(activeOrderRepo.findById(activeOrder.getId()));
    }

    /** Issuance stub that returns a fixed code and records compensation cancel calls. */
    private static class RecordingIssuanceService implements ITicketIssuanceService {
        private final String code = "TIX-COMP-1";
        private int cancelCount = 0;
        private String cancelledCode;

        @Override
        public String issueTickets(String activeOrderId, TicketIssuanceRequestDTO request) {
            return code;
        }

        @Override
        public boolean cancelIssuedTickets(String externalTicketCode) {
            cancelCount++;
            cancelledCode = externalTicketCode;
            return true;
        }
    }

    @Test
    void givenSeatedActiveOrderForMember_whenCheckout_thenIssuanceRequestCarriesRealCustomerEventAndSeats() throws Exception {
        // Arrange - a seated zone with one seat at row 1 / seat 1
        Show show = storeSeatedShow(eventId, VALID_ZONE_ID, VALID_ZONE_NAME, VALID_SEAT_ID, 1, 1, VALID_TICKET_PRICE);

        Ticket ticket = new Ticket(TICKET_ID_1, show.getShowId(), VALID_ZONE_ID, VALID_SEAT_ID, VALID_TICKET_PRICE);
        ticketRepo.store(ticket);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, eventId, show.getShowId(), ticket, DEFAULT_LOCK_MINUTES);
        storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        linkActiveOrderToMember(VALID_EMAIL, activeOrder.getId());

        CapturingTicketIssuanceService capturing = new CapturingTicketIssuanceService();
        OrderService seatedService = new OrderService(orderRepo, activeOrderRepo, paymentGateway, capturing,
                memberRepo, showRepo, ticketRepo, notificationService, eventRepo, companyRepo, TEST_ORDER_PROPERTIES);

        // Act
        seatedService.checkout(activeOrder.getId(), VALID_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS);

        // Assert - the request threaded the real customer (member email), event, zone and seat
        TicketIssuanceRequestDTO request = capturing.captured;
        assertNotNull(request);
        assertEquals(VALID_EMAIL, request.customerId());
        assertEquals(eventId, request.eventId());
        assertEquals(VALID_ZONE_NAME, request.zoneName());
        assertFalse(request.standing());
        assertEquals(List.of(new TicketIssuanceRequestDTO.SeatRef(1, 1)), request.seats());
    }

    /** Records the request handed to the issuance service so the test can assert on threaded data. */
    private static class CapturingTicketIssuanceService implements ITicketIssuanceService {
        private TicketIssuanceRequestDTO captured;

        @Override
        public String issueTickets(String activeOrderId, TicketIssuanceRequestDTO request) {
            this.captured = request;
            return "TIX-CAP-1";
        }

        @Override
        public boolean cancelIssuedTickets(String externalTicketCode) {
            return true;
        }
    }

    @Test
    void givenExpiredActiveOrder_whenCheckout_thenThrowsAndNoOrderCreated() {

        // Arrange
        Show show = storeShowWithSeat(eventId, VALID_ZONE_ID, VALID_SEAT_ID);
        Ticket ticket = storeTicket(TICKET_ID_1, VALID_TICKET_PRICE);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, eventId, show.getShowId(), ticket, DEFAULT_LOCK_MINUTES);
        // Simulate the seat lock that ActiveOrderService performs on reservation
        show.getVenueMap().lockSeatsInZone(VALID_ZONE_ID, java.util.List.of(VALID_SEAT_ID));
        showRepo.store(show);
        // Force the active order into an expired state without waiting on a real clock
        activeOrder.setExpirationTime(LocalDateTime.now().minusSeconds(1));

        // Act & Assert
        assertThrows(Exception.class, () ->
                orderService.checkout(activeOrder.getId(), VALID_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS));

        // Assert - no Order was created
        assertEquals(0, orderRepo.getAll().size());

        // Assert - expired ActiveOrder is deleted
        assertNull(activeOrderRepo.findById(activeOrder.getId()));

        // Assert - seat is not taken
        assertFalse(showRepo.findById(show.getShowId()).getVenueMap().getZoneById(VALID_ZONE_ID).getSeats().stream().anyMatch(seat -> seat.getId().equals(VALID_SEAT_ID) && seat.isTaken()));
    }

    @Test
    void givenExpiredActiveOrderLinkedToMember_whenCheckout_thenThrowsAndMemberActiveOrderCleared() {

        // Arrange
        Show show = storeShowWithSeat(eventId, VALID_ZONE_ID, VALID_SEAT_ID);
        Ticket ticket = storeTicket(TICKET_ID_1, VALID_TICKET_PRICE);
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, eventId, show.getShowId(), ticket, DEFAULT_LOCK_MINUTES);
        linkActiveOrderToMember(VALID_EMAIL, activeOrder.getId());
        // Simulate the seat lock that ActiveOrderService performs on reservation
        show.getVenueMap().lockSeatsInZone(VALID_ZONE_ID, java.util.List.of(VALID_SEAT_ID));
        showRepo.store(show);
        // Force the active order into an expired state without waiting on a real clock
        activeOrder.setExpirationTime(LocalDateTime.now().minusSeconds(1));

        // Act & Assert
        assertThrows(Exception.class, () ->
                orderService.checkout(activeOrder.getId(), VALID_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS));

        // Assert - no Order was created
        assertEquals(0, orderRepo.getAll().size());

        // Assert - expired ActiveOrder is deleted
        assertNull(activeOrderRepo.findById(activeOrder.getId()));

        // Assert - seat is not taken
        assertFalse(showRepo.findById(show.getShowId()).getVenueMap().getZoneById(VALID_ZONE_ID).getSeats().stream().anyMatch(seat -> seat.getId().equals(VALID_SEAT_ID) && seat.isTaken()));

        // Assert - member's active order reference is cleared
        assertNull(memberRepo.findByEmail(VALID_EMAIL).getActiveOrderId());

        // Assert - member has no completed orders (expiry is not a purchase)
        assertEquals(0, memberRepo.findByEmail(VALID_EMAIL).getOrderIds().size());
    }

    @Test
    void givenEventWithMinAgePolicy_whenCheckoutWithUnderageCustomer_thenThrowsExceptionAndOrderNotCreated() throws Exception {
        // Arrange
        Ticket ticket = storeTicket(TICKET_ID_1, VALID_TICKET_PRICE);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, VALID_EVENT_ID, VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);

        setEventPurchasePolicy(VALID_EVENT_ID, MIN_AGE_18_POLICY);

        // Act & Assert
        assertThrows(Exception.class, () -> {
            orderService.checkout(activeOrder.getId(), UNDERAGE_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS);
        });

        assertNull(orderRepo.findById(activeOrder.getId()));

        assertNotNull(activeOrderRepo.findById(activeOrder.getId()));
    }

    @Test
    void givenEventWithCompositeAndPolicy_whenUnderageCustomerCheckouts_thenThrowsException() throws Exception {
        // Arrange
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Event enforces BOTH min age 18 AND max tickets 2
        setEventPurchasePolicy(event.getEventId(), AND_POLICY);

        Ticket ticket = storeTicket(TICKET_ID_1, VALID_TICKET_PRICE);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);

        // Act & Assert
        // Customer buys 1 ticket (valid for max tickets) but is a minor (violates AND branch)
        assertThrows(Exception.class, () -> {
            orderService.checkout(activeOrder.getId(), UNDERAGE_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS);
        });
        assertNull(orderRepo.findById(ORDER_ID_1));
    }

    @Test
    void givenEventWithCompositeOrPolicy_whenUnderageCustomerBuysValidTicketCount_thenSuccess() throws Exception {
        // Arrange
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Event enforces OR policy: Max 2 tickets OR Min Age 18
        setEventPurchasePolicy(event.getEventId(), OR_POLICY);

        Ticket ticket = storeTicket(TICKET_ID_1, VALID_TICKET_PRICE);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);

        // Act
        // Customer is underage, but only buys 1 ticket, satisfying the "Max 2 tickets" condition of the OR branch
        OrderDTO result = orderService.checkout(activeOrder.getId(), UNDERAGE_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS);

        // Assert
        assertNotNull(result);
        assertEquals(VALID_TICKET_PRICE, result.getFinalPrice());
    }

    @Test
    void givenBothCompanyAndEventHavePolicies_whenCustomerSatisfiesBoth_thenSuccess() throws Exception {
        // Arrange
        // Company enforces minimum age 18
        setCompanyPurchasePolicy(VALID_COMPANY_NAME, MIN_AGE_18_POLICY);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        // Event enforces maximum of 2 tickets per order
        setEventPurchasePolicy(event.getEventId(), MAX_TICKETS_2_POLICY);

        Ticket ticket = storeTicket(TICKET_ID_1, VALID_TICKET_PRICE);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);

        // Act
        // Valid adult customer purchasing 1 ticket satisfies both company and event policies
        OrderDTO result = orderService.checkout(activeOrder.getId(), VALID_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS);

        // Assert
        assertNotNull(result);
        assertNotNull(orderRepo.findById(result.getOrderId()));
    }

    @Test
    void givenBothCompanyAndEventHavePolicies_whenCustomerSatisfiesEventButFailsCompany_thenThrowsException() throws Exception {
        // Arrange
        // Company enforces minimum age 18
        setCompanyPurchasePolicy(VALID_COMPANY_NAME, MIN_AGE_18_POLICY);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        // Event enforces maximum of 2 tickets per order
        setEventPurchasePolicy(event.getEventId(), MAX_TICKETS_2_POLICY);

        Ticket ticket = storeTicket(TICKET_ID_1, VALID_TICKET_PRICE);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);

        // Act & Assert
        // Underage customer satisfies the event's ticket capacity rule but fails the company's age restriction
        assertThrows(Exception.class, () -> {
            orderService.checkout(activeOrder.getId(), UNDERAGE_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS);
        });
        assertNull(orderRepo.findById(ORDER_ID_1));
    }

    //testing checkout with different policies

    @Test
    void givenCompanyWithSimpleDiscount_whenCheckout_thenDiscountIsApplied() throws Exception {
        // Arrange
        // Company gives 10% flat discount on all its events
        setCompanyDiscountPolicy(VALID_COMPANY_NAME, DISCOUNT_SIMPLE_10);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Ticket ticket = storeTicket(TICKET_ID_1, 100.0);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);

        // Act
        OrderDTO result = orderService.checkout(activeOrder.getId(), VALID_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS);

        // Assert
        // 10% discount from 100.0 initial price leaves 90.0 final price
        assertNotNull(result);
        assertEquals(90.0, result.getFinalPrice());
    }

    @Test
    void givenEventWithCompositeMaxDiscount_whenCheckout_thenHighestDiscountIsApplied() throws Exception {
        // Arrange
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Event has a composite MAX policy containing a 10% and a 20% discount
        setEventDiscountPolicy(event.getEventId(), DISCOUNT_COMPOSITE_MAX);

        Ticket ticket = storeTicket(TICKET_ID_1, 100.0);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);

        // Act
        OrderDTO result = orderService.checkout(activeOrder.getId(), VALID_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS);

        // Assert
        // The MAX policy should select the highest discount branch, which is 20% (100.0 -> 80.0)
        assertNotNull(result);
        assertEquals(80.0, result.getFinalPrice());
    }

    @Test
    void givenEventWithCompositeSumDiscount_whenCheckout_thenTotalAccumulatedDiscountIsApplied() throws Exception {
        // Arrange
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Event has a composite SUM policy combining a 10% and a 20% discount
        setEventDiscountPolicy(event.getEventId(), DISCOUNT_COMPOSITE_SUM);

        Ticket ticket = storeTicket(TICKET_ID_1, 100.0);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);

        // Act
        OrderDTO result = orderService.checkout(activeOrder.getId(), VALID_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS);

        // Assert
        // The SUM policy accumulates the discounts: 10% + 20% = 30% discount (100.0 -> 70.0)
        assertNotNull(result);
        assertEquals(70.0, result.getFinalPrice());
    }

    @Test
    void givenEventWithActiveTimeBasedDiscount_whenCheckout_thenDiscountIsApplied() throws Exception {
        // Arrange
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Active window discount (15%) is applied to the event
        setEventDiscountPolicy(event.getEventId(), DISCOUNT_TIME_VALID);
        storeShow(event.getEventId(), true);

        Ticket ticket = storeTicket(TICKET_ID_1, 100.0);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);

        // Act
        OrderDTO result = orderService.checkout(activeOrder.getId(), VALID_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS);

        // Assert
        assertNotNull(result);
        assertEquals(85.0, result.getFinalPrice());
    }

    @Test
    void givenEventWithExpiredTimeBasedDiscount_whenCheckout_thenNoDiscountIsApplied() throws Exception {
        // Arrange
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Expired discount rule (15%) is applied to the event
        setEventDiscountPolicy(event.getEventId(), DISCOUNT_TIME_EXPIRED);

        Ticket ticket = storeTicket(TICKET_ID_1, 100.0);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);

        // Act
        OrderDTO result = orderService.checkout(activeOrder.getId(), VALID_CUSTOMER_DETAILS, VALID_PAYMENT_DETAILS);

        // Assert
        // Discount window has already expired, price remains 100.0 (0% discount)
        assertNotNull(result);
        assertEquals(100.0, result.getFinalPrice());
    }

    @Test
    void givenConditionalTicketsDiscount_whenCustomerOrderFailsCondition_thenNoDiscountIsApplied() throws Exception {
        // Arrange
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Discount (5%) is conditional upon buying a MAXIMUM of 2 tickets
        setEventDiscountPolicy(event.getEventId(), DISCOUNT_CONDITIONAL_MAX_2);

        // Customer buys 3 tickets, exceeding the allowed maximum for the discount condition
        Ticket ticket1 = storeTicket(TICKET_ID_1, 100.0);
        Ticket ticket2 = storeTicket("ticket2", 100.0);
        Ticket ticket3 = storeTicket("ticket3", 100.0);

        ActiveOrder activeOrder = storeActiveOrder(ACTIVE_ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, DEFAULT_LOCK_MINUTES);
        activeOrder.addTicket(ticket1.getId(), ticket1.getPrice());
        activeOrder.addTicket(ticket2.getId(), ticket2.getPrice());
        activeOrder.addTicket(ticket3.getId(), ticket3.getPrice());
        activeOrderRepo.store(activeOrder);

        // Act
        OrderDTO result = orderService.checkout(activeOrder.getId(), CUSTOMER_WITH_3_TICKETS, VALID_PAYMENT_DETAILS);

        // Assert
        // Total price should remain 300.0 since the condition (max 2 tickets) was violated
        assertNotNull(result);
        assertEquals(300.0, result.getFinalPrice());
    }
    @Test
    void givenEventWithCouponDiscount_whenCustomerUsesValidCoupon_thenDiscountIsApplied() throws Exception {
        // Arrange
        // 1. Create a dynamic fresh event
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        String realEventId = event.getEventId();

        // 2. Attach the coupon discount policy to this event
        setEventDiscountPolicy(realEventId, DISCOUNT_COUPON_15);

        // 3. Create a dynamic show linked to the event
        Show show = storeShow(realEventId, true);
        String realShowId = show.getShowId();

        // 4. Create and store a ticket linked to the real show ID
        Ticket ticket = new Ticket(TICKET_ID_1, realShowId, VALID_ZONE_ID, VALID_SEAT_ID, 100.0);
        ticketRepo.store(ticket);

        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, realEventId, realShowId, ticket, DEFAULT_LOCK_MINUTES);

        // Act
        OrderDTO result = orderService.checkout(activeOrder.getId(), CUSTOMER_WITH_COUPON, VALID_PAYMENT_DETAILS);

        // Assert
        // The coupon is valid and matches, so 15% discount must be applied (100.0 -> 85.0)
        assertNotNull(result);
        assertEquals(85.0, result.getFinalPrice());
    }



    @Test
    void givenValidOrderAndNoDiscount_whenPreviewOrderPrice_thenReturnExactBasePrice() throws Exception {
        // Arrange
        Ticket ticket = storeTicket(TICKET_ID_1, 100.0);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, eventId, VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);

        // Act
        OrderPricePreviewDTO result = orderService.previewOrderPrice(activeOrder.getId(), VALID_CUSTOMER_DETAILS);

        // Assert
        assertNotNull(result);
        assertEquals(100.0, result.getBasePrice());
        assertEquals(100.0, result.getFinalPrice());
        assertEquals(0.0, result.getTotalDiscountAmount());

        // Assert - ActiveOrder is NOT deleted during preview
        assertNotNull(activeOrderRepo.findById(activeOrder.getId()));
    }

    @Test
    void givenValidOrderWithCoupon_whenPreviewOrderPrice_thenReturnDiscountedPriceDTO() throws Exception {
        // Arrange
        // Attach the coupon discount policy (15%) to the event
        setEventDiscountPolicy(eventId, DISCOUNT_COUPON_15);

        Ticket ticket = storeTicket(TICKET_ID_1, 200.0);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, eventId, VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);

        // Act
        // Use a customer who has the matching coupon code in their details
        OrderPricePreviewDTO result = orderService.previewOrderPrice(activeOrder.getId(), CUSTOMER_WITH_COUPON);

        // Assert
        assertNotNull(result);
        assertEquals(200.0, result.getBasePrice());
        assertEquals(30.0, result.getTotalDiscountAmount()); // 15% of 200 is 30
        assertEquals(170.0, result.getFinalPrice());

        // Assert - ActiveOrder is NOT deleted during preview
        assertNotNull(activeOrderRepo.findById(activeOrder.getId()));
    }

    @Test
    void givenExpiredActiveOrder_whenPreviewOrderPrice_thenThrowsAndReleasesSeats() {
        // Arrange
        Show show = storeShowWithSeat(eventId, VALID_ZONE_ID, VALID_SEAT_ID);
        Ticket ticket = storeTicket(TICKET_ID_1, VALID_TICKET_PRICE);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, eventId, show.getShowId(), ticket, DEFAULT_LOCK_MINUTES);

        // Simulate the seat lock
        show.getVenueMap().lockSeatsInZone(VALID_ZONE_ID, java.util.List.of(VALID_SEAT_ID));
        showRepo.store(show);

        // Force the active order into an expired state
        activeOrder.setExpirationTime(LocalDateTime.now().minusSeconds(1));
        activeOrderRepo.store(activeOrder);

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () ->
                orderService.previewOrderPrice(activeOrder.getId(), VALID_CUSTOMER_DETAILS));

        assertEquals("Active Order has expired", exception.getMessage());

        // Assert - expired ActiveOrder is deleted
        assertNull(activeOrderRepo.findById(activeOrder.getId()));

        // Assert - seat is released back to the show
        assertFalse(showRepo.findById(show.getShowId()).getVenueMap()
                .getZoneById(VALID_ZONE_ID).getSeats().stream()
                .anyMatch(seat -> seat.getId().equals(VALID_SEAT_ID) && seat.isTaken()));
    }


}
