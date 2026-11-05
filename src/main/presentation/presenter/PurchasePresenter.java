package presentation.presenter;

import application.dto.ActiveOrderDTO;
import application.dto.OrderDTO;
import application.dto.OrderPricePreviewDTO;
import application.dto.PaymentDetailsDTO;
import application.dto.ShowDTO;
import application.service.ActiveOrderService;
import application.service.EventService;
import application.service.OrderService;
import application.service.ShowService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import domain.Aggregates.order.CustomerDetails;
import domain.exceptions.EventNotFoundException;
import domain.exceptions.ShowNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import presentation.CurrentUser;
import presentation.Result;

import java.time.LocalDateTime;
import java.util.List;

@Component
@VaadinSessionScope
public class PurchasePresenter {

    private static final Logger logger = LoggerFactory.getLogger(PurchasePresenter.class);

    private final ActiveOrderService activeOrderService;
    private final OrderService orderService;
    private final EventService eventService;
    private final ShowService showService;
    private final CurrentUser currentUser;

    public PurchasePresenter(ActiveOrderService activeOrderService,
                             OrderService orderService,
                             EventService eventService,
                             ShowService showService,
                             CurrentUser currentUser) {
        this.activeOrderService = activeOrderService;
        this.orderService = orderService;
        this.eventService = eventService;
        this.showService = showService;
        this.currentUser = currentUser;
    }

    public Result<ActiveOrderDTO> reserveTickets(String showId, String zoneId,
                                                 List<String> seatIds, int standingQty,
                                                 String raffleCode) {
        try {
            String token = currentUser.getToken();
            String guestId = currentUser.isLoggedIn() ? null : currentUser.getGuestSessionId();
            ActiveOrderDTO dto = activeOrderService.reserveTickets(
                    showId, token, guestId, raffleCode, zoneId, seatIds, standingQty);
            if (currentUser.isLoggedIn()) {
                currentUser.setActiveOrderId(dto.id);
            }
            return Result.ok(dto);
        } catch (Exception e) {
            logger.warn("Failed to reserve tickets for show={}: {}", showId, e.getMessage());
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    /**
     * Returns the logged-in member's current unexpired active order, or null if none.
     * Used by views that need to branch on whether the user already has an order open.
     */
    public ActiveOrderDTO getCurrentActiveOrder() {
        try {
            String token = currentUser.getToken();
            String id = activeOrderService.getActiveOrderIdForMember(token);
            if (id == null) return null;
            return activeOrderService.getActiveOrder(id);
        } catch (Exception e) {
            return null;
        }
    }

    public Result<ActiveOrderDTO> addTicketsToOrder(String orderId, String zoneId,
                                                    List<String> seatIds, int standingQty) {
        try {
            String token = currentUser.getToken();
            ActiveOrderDTO dto = activeOrderService.addTicketsToOrder(
                    orderId, token, zoneId, seatIds, standingQty);
            return Result.ok(dto);
        } catch (Exception e) {
            logger.warn("Failed to add tickets to order={}: {}", orderId, e.getMessage());
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> removeTicket(String orderId, String ticketId) {
        try {
            activeOrderService.removeTicketFromOrder(orderId, ticketId);
            return Result.ok(null);
        } catch (Exception e) {
            logger.warn("Failed to remove ticket={} from order={}: {}", ticketId, orderId, e.getMessage());
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<ActiveOrderDTO> getActiveOrder(String activeOrderId) {
        try {
            ActiveOrderDTO dto = activeOrderService.getActiveOrder(activeOrderId);
            if (dto.expirationTime != null && dto.expirationTime.isBefore(LocalDateTime.now())) {
                // The repo still holds expired orders (no scheduled purge), so getActiveOrder
                // would otherwise return a dead order and render a checkout form that can't
                // succeed. Clear the cached id here so the resume banner stops offering it.
                if (currentUser.isLoggedIn()) {
                    currentUser.setActiveOrderId(null);
                }
                return Result.fail("Your reservation has expired.");
            }
            return Result.ok(dto);
        } catch (Exception e) {
            logger.warn("Failed to load active order id={}: {}", activeOrderId, e.getMessage());
            return Result.fail("Order not found or has expired.");
        }
    }

    public Result<OrderDTO> checkout(String activeOrderId, String name, String email,
                                     String phone, int age, int ticketQuantity,
                                     String couponCode, PaymentDetailsDTO paymentDetails) {
        try {
            CustomerDetails details = CustomerDetails.builder()
                    .name(name)
                    .email(email)
                    .phone(phone)
                    .age(age)
                    .ticketQuantity(ticketQuantity)
                    .purchaseTime(LocalDateTime.now())
                    .couponCode(couponCode)
                    .build();
            OrderDTO order = orderService.checkout(activeOrderId, details, paymentDetails);
            if (currentUser.isLoggedIn()) {
                currentUser.setActiveOrderId(null);
            }
            return Result.ok(order);
        } catch (Exception e) {
            logger.warn("Checkout failed for active order id={}: {}", activeOrderId, e.getMessage());
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<OrderPricePreviewDTO> previewCoupon(String activeOrderId, String couponCode, int ticketQuantity) {
        try {
            CustomerDetails details = CustomerDetails.builder()
                    .couponCode(couponCode)
                    .ticketQuantity(ticketQuantity)
                    .build();
            OrderPricePreviewDTO preview = orderService.previewOrderPrice(activeOrderId, details);
            return Result.ok(preview);
        } catch (Exception e) {
            logger.warn("Coupon preview failed for active order id={}: {}", activeOrderId, e.getMessage());
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<String> getEventName(String eventId) {
        try {
            return Result.ok(eventService.getEventById(eventId).getName());
        } catch (EventNotFoundException e) {
            return Result.fail("Event not found.");
        } catch (Exception e) {
            logger.error("Unexpected error loading event id={}", eventId, e);
            return Result.fail("Failed to load event.");
        }
    }

    public Result<ShowDTO> getShow(String showId) {
        try {
            return Result.ok(showService.getShowById(showId));
        } catch (ShowNotFoundException e) {
            return Result.fail("Show not found.");
        } catch (Exception e) {
            logger.error("Unexpected error loading show id={}", showId, e);
            return Result.fail("Failed to load show.");
        }
    }
}
