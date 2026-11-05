package application.service;

import application.dto.OrderDTO;
import application.dto.OrderPricePreviewDTO;
import application.dto.PaymentDetailsDTO;
import application.dto.TicketIssuanceRequestDTO;
import application.interfaces.INotificationService;
import application.interfaces.IPaymentGateway;
import application.interfaces.ITicketIssuanceService;
import domain.Aggregates.activeorder.ActiveOrder;
import domain.Aggregates.event.Event;
import domain.Aggregates.order.CustomerDetails;
import domain.Aggregates.order.Order;
import domain.Aggregates.productioncompany.ProductionCompany;
import domain.Aggregates.show.SeatPosition; // value object for seat row/number
import domain.Aggregates.show.Show;
import domain.Aggregates.ticket.Ticket;
import domain.Aggregates.member.Member;
import domain.Irepository.*;
import domain.exceptions.OptimisticLockException;
import infra.config.OrderProperties;
import domain.exceptions.PolicyEvaluationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class OrderService {

    private final IOrderRepository orderRepo;
    private final IActiveOrderRepository activeOrderRepo;
    private final IPaymentGateway paymentGateway;
    private final ITicketIssuanceService ticketIssuanceService;
    private final IMemberRepository memberRepo;
    private final IShowRepository showRepo;
    private final ITicketRepository ticketRepo;
    private final INotificationService notificationService;
    private final IEventRepository eventRepo;
    private final IProductionCompanyRepository productionCompanyRepo;
    private final OrderProperties orderProperties;

    public OrderService(IOrderRepository orderRepo,
                        IActiveOrderRepository activeOrderRepo,
                        IPaymentGateway paymentGateway,
                        ITicketIssuanceService ticketIssuanceService,
                        IMemberRepository memberRepo,
                        IShowRepository showRepo,
                        ITicketRepository ticketRepo,
                        INotificationService notificationService,
                        IEventRepository eventRepo,
                        IProductionCompanyRepository productionCompanyRepo,
                        OrderProperties orderProperties) {
        this.orderRepo = orderRepo;
        this.activeOrderRepo = activeOrderRepo;
        this.paymentGateway = paymentGateway;
        this.ticketIssuanceService = ticketIssuanceService;
        this.memberRepo = memberRepo;
        this.showRepo = showRepo;
        this.ticketRepo = ticketRepo;
        this.notificationService = notificationService;
        this.eventRepo = eventRepo;
        this.productionCompanyRepo = productionCompanyRepo;
        this.orderProperties = orderProperties;
    }

    /**
     * UC7 - Checkout
     * Completes a reservation by processing payment, issuing tickets,
     * creating a final Order, and deleting the consumed ActiveOrder.
     *
     * @param activeOrderId   ID of the active order to check out
     * @param customerDetails Customer's personal details
     * @param paymentDetails  Card / payer details passed to the payment gateway
     * @return the newly created Order
     * @throws Exception on expiry, payment failure, or issuance failure
     */
    public OrderDTO checkout(String activeOrderId, CustomerDetails customerDetails, PaymentDetailsDTO paymentDetails) throws Exception {

        log.info("Initiating checkout for Active Order ID: {}", activeOrderId);

        // Step 1: fetch and validate the active order
        ActiveOrder activeOrder = activeOrderRepo.findById(activeOrderId);
        if (activeOrder == null) {
            log.warn("Checkout aborted: Active Order ID {} not found", activeOrderId);
            throw new Exception("Active Order not found");
        }

        // Step 2: active order is expired, remove it and release seats
        if (activeOrder.isExpired(LocalDateTime.now())) {
            removeActiveOrderAndReleaseSeats(activeOrder);
            log.warn("Checkout aborted: Active Order ID {} has expired and was removed", activeOrderId);
            throw new Exception("Active Order has expired");
        }
        // Step 2.5: Policy evaluation & discount calculation
        // validate policy
        validatePurchaseAllowed(activeOrder, customerDetails);
        //discount calculation
        double totalPrice = calculateFinalPrice(activeOrder, customerDetails);

        // Step 3: process payment — the returned transaction id becomes the order id
        String transactionId = paymentGateway.processPayment(activeOrderId, totalPrice, paymentDetails);
        if (transactionId == null) {
            log.warn("Checkout aborted: Payment failed for Active Order ID {}", activeOrderId);
            throw new Exception("Payment failed");
        }

        // Step 4: issue tickets — refund if issuance fails. The buyer (member, if any) is
        // resolved here so the issuance request can carry the real customer id.
        Member member = memberRepo.findByActiveOrderId(activeOrderId);
        TicketIssuanceRequestDTO issuanceRequest = buildTicketIssuanceRequestDTO(activeOrder, member, paymentDetails);
        String externalTicketCode = ticketIssuanceService.issueTickets(activeOrderId, issuanceRequest);
        if (externalTicketCode == null) {
            log.warn("Ticket issuance failed for Active Order ID {}. Initiating refund.", activeOrderId);
            paymentGateway.refundPayment(transactionId, totalPrice);
            throw new Exception("Ticket issuance failed");
        }

        // From here payment AND issuance have succeeded. If any remaining step throws, the
        // @Transactional rollback undoes the DB writes but NOT the two external side effects,
        // so we compensate explicitly (cancel the issued ticket + refund) to avoid an orphaned
        // charge / issued ticket, then rethrow.
        try {
            // Step 5: create the final Order — the payment transaction id is the order id, a single
            // stable identifier that also lets refunds target the external transaction without a side map
            Order order = new Order(
                    transactionId,
                    activeOrder.getEventId(),
                    activeOrder.getShowId(),
                    List.copyOf(activeOrder.getTicketIds()),
                    totalPrice,
                    LocalDateTime.now(),
                    customerDetails);
            order.setExternalTicketCode(externalTicketCode);
            orderRepo.store(order);
            log.info("Order ID {} created successfully", transactionId);

            // Update the Event aggregate's sales counters so reports read from the root
            int ticketCount = activeOrder.getTicketIds().size();
            for (int attempt = 0; attempt < orderProperties.salesCounterMaxRetries(); attempt++) {
                Event soldEvent = eventRepo.findById(activeOrder.getEventId());
                if (soldEvent == null) break;
                try {
                    soldEvent.recordSale(ticketCount, totalPrice);
                    eventRepo.store(soldEvent);
                    break;
                } catch (OptimisticLockException e) {
                    log.warn("Optimistic lock conflict updating sales counters for event id={}, attempt {}",
                            activeOrder.getEventId(), attempt + 1);
                }
            }

            // Link to Member if applicable, and notify the buyer of the successful purchase
            // (member was resolved before issuance, above)
            if (member != null) {
                member.completeActiveOrder(order.getOrderId());

                Show show = showRepo.findById(order.getShowId());
                String showDate = (show != null && show.getDate() != null)
                        ? show.getDate().format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")) : "unknown date";
                notificationService.sendNotification(member.getEmail(), "Purchase Successful",
                        "Your tickets for the show on " + showDate + " have been purchased successfully.", null);
                // Associations handled via recipientEmail in repository

                memberRepo.store(member);
                log.info("Order ID {} linked to Member {}", activeOrderId, member.getEmail());
            }

            // Step 6: delete the consumed ActiveOrder
            activeOrderRepo.delete(activeOrderId);
            log.info("Active Order ID {} deleted after successful checkout", activeOrderId);

            return OrderDTO.fromEntity(order);
        } catch (Exception e) {
            log.error("Post-issuance step failed for txn {}; compensating: cancel ticket + refund",
                    transactionId, e);
            ticketIssuanceService.cancelIssuedTickets(externalTicketCode); // best-effort
            paymentGateway.refundPayment(transactionId, totalPrice);       // best-effort
            throw e;
        }
    }

    private void validatePurchaseAllowed(ActiveOrder activeOrder, CustomerDetails customerDetails) throws Exception{
        // 1. Fetch the Event aggregate associated with this active order
        Event event = eventRepo.findById(activeOrder.getEventId());
        if (event == null) {
            log.warn("Checkout aborted: Event ID {} not found", activeOrder.getEventId());
            throw new Exception("Event not found");
        }
        // 2. Fetch the Production Company using the identifier stored in the Event
        ProductionCompany company = productionCompanyRepo.findById(event.getProductionCompanyName());
        if (company == null) {
            log.warn("Checkout aborted: Production Company '{}' not found for Event ID {}",
                    event.getProductionCompanyName(), activeOrder.getEventId());
            throw new Exception("Production Company not found");
        }

        // 3. Validate the purchase
        event.isPurchaseAllowed(customerDetails, company);

    }

    /**
     * Resolves the real customer / event / zone / seat data for an order into a
     * {@link TicketIssuanceRequestDTO}. An order's tickets always belong to a single zone, so the
     * zone (and standing-vs-seated) is read once from the order's first ticket. Standing orders
     * carry a quantity; seated orders carry the per-ticket seat row/number.
     *
     * <p>If the show or zone cannot be resolved, the request degrades to a standing order with a
     * null zone name (the adapter falls back to its configured default zone).
     */
    private TicketIssuanceRequestDTO buildTicketIssuanceRequestDTO(ActiveOrder activeOrder, Member member,
                                                             PaymentDetailsDTO paymentDetails) {
        String customerId = member != null ? member.getEmail() : paymentDetails.getPayerId();
        String eventId = activeOrder.getEventId();
        List<String> ticketIds = activeOrder.getTicketIds();

        Show show = showRepo.findById(activeOrder.getShowId());
        Ticket firstTicket = ticketIds.isEmpty() ? null : ticketRepo.findById(ticketIds.get(0));

        if (show == null || firstTicket == null) {
            log.warn("Could not resolve zone for Active Order ID {} (show or ticket missing); "
                    + "issuing as standing with default zone", activeOrder.getId());
            return new TicketIssuanceRequestDTO(customerId, eventId, null, true, ticketIds.size(), List.of());
        }

        // All of an order's tickets share one zone; the first ticket tells us standing vs. seated.
        String zoneName = show.getZoneName(firstTicket.getZoneId());

        if (firstTicket.isStanding()) {
            return new TicketIssuanceRequestDTO(customerId, eventId, zoneName, true, ticketIds.size(), List.of());
        }

        List<TicketIssuanceRequestDTO.SeatRef> seats = new java.util.ArrayList<>(ticketIds.size());
        for (String ticketId : ticketIds) {
            Ticket ticket = ticketRepo.findById(ticketId);
            if (ticket == null) continue;
            SeatPosition position = show.getSeatPosition(ticket.getZoneId(), ticket.getSeatId());
            if (position != null) {
                seats.add(new TicketIssuanceRequestDTO.SeatRef(position.row(), position.number()));
            }
        }
        return new TicketIssuanceRequestDTO(customerId, eventId, zoneName, false, seats.size(), seats);
    }

    private void removeActiveOrderAndReleaseSeats(ActiveOrder activeOrder) {
        Show show = showRepo.findById(activeOrder.getShowId());
        if (show == null) {
            log.warn("Could not release seats: Show ID {} not found for expired Active Order ID {}",
                    activeOrder.getShowId(), activeOrder.getId());
        } else {
            for (String ticketId : activeOrder.getTicketIds()) {
                Ticket ticket = ticketRepo.findById(ticketId);
                if (ticket == null) {
                    log.warn("Could not release seat: Ticket ID {} not found", ticketId);
                    continue;
                }
                show.releaseTicket(ticket);
                ticketRepo.delete(ticketId);
            }
            showRepo.store(show);
            log.info("Released {} ticket(s) back to Show ID {} for expired Active Order ID {}",
                    activeOrder.getTicketIds().size(), activeOrder.getShowId(), activeOrder.getId());
        }

        //Remove linked member if need
        Member member = memberRepo.findByActiveOrderId(activeOrder.getId());
            if (member != null) {
                member.expireActiveOrder();
                memberRepo.store(member);
                log.info("Member {} unlinked from Active Order ID {}", member.getEmail(), activeOrder.getId());
            }
        
        activeOrderRepo.delete(activeOrder.getId());
    }

    public OrderPricePreviewDTO previewOrderPrice(String activeOrderId, CustomerDetails customerDetails) throws Exception {
        ActiveOrder activeOrder = activeOrderRepo.findById(activeOrderId);
        if (activeOrder == null) throw new Exception("Active Order not found");

        if (activeOrder.isExpired(LocalDateTime.now())) {
            removeActiveOrderAndReleaseSeats(activeOrder);
            throw new Exception("Active Order has expired");
        }

        double basePrice = activeOrder.getTotalPrice();
        double finalPrice = calculateFinalPrice(activeOrder, customerDetails);
        double discountAmount = basePrice - finalPrice;


        return OrderPricePreviewDTO.builder()
                .basePrice(basePrice)
                .totalDiscountAmount(discountAmount)
                .finalPrice(finalPrice)
                .build();
    }

    private double calculateFinalPrice(ActiveOrder activeOrder, CustomerDetails customerDetails) throws Exception {
        Event event = eventRepo.findById(activeOrder.getEventId());
        if (event == null) throw new Exception("Event not found");

        ProductionCompany company = productionCompanyRepo.findById(event.getProductionCompanyName());
        if (company == null) throw new Exception("Production Company not found");

        double baseTotalPrice = activeOrder.getTotalPrice();

        double totalPrice = event.calculateDiscountedPrice(baseTotalPrice, customerDetails, company);

        return totalPrice;
    }
}


