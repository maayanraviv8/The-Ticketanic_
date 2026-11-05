package presentation.presenter;

import application.dto.OrderDTO;
import application.dto.ShowDTO;
import application.dto.TicketDTO;
import application.service.EventService;
import application.service.MemberService;
import application.service.ShowService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import domain.exceptions.EventNotFoundException;
import domain.exceptions.ShowNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import presentation.CurrentUser;
import presentation.Result;

import java.util.List;

@Component
@VaadinSessionScope
public class OrderHistoryPresenter {

    private static final Logger logger = LoggerFactory.getLogger(OrderHistoryPresenter.class);

    private final MemberService memberService;
    private final EventService eventService;
    private final ShowService showService;
    private final CurrentUser currentUser;

    public OrderHistoryPresenter(MemberService memberService,
                                 EventService eventService,
                                 ShowService showService,
                                 CurrentUser currentUser) {
        this.memberService = memberService;
        this.eventService = eventService;
        this.showService = showService;
        this.currentUser = currentUser;
    }

    public Result<List<OrderDTO>> getOrderHistory() {
        try {
            return Result.ok(memberService.getOrderHistory(currentUser.getToken()));
        } catch (Exception e) {
            logger.warn("Failed to load order history for {}: {}", currentUser.getEmail(), e.getMessage());
            return Result.fail("Could not load your order history.");
        }
    }

    public Result<List<TicketDTO>> getTickets(List<String> ticketIds) {
        try {
            return Result.ok(memberService.getTicketsByIds(ticketIds, currentUser.getToken()));
        } catch (Exception e) {
            logger.warn("Failed to load tickets for {}: {}", currentUser.getEmail(), e.getMessage());
            return Result.fail("Could not load ticket details.");
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
