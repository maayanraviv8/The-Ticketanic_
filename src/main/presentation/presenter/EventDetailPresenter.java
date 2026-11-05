package presentation.presenter;

import application.dto.EventDTO;
import application.dto.RaffleStatusDTO;
import application.dto.ShowDTO;
import application.dto.VenueMapDTO;
import application.service.EventService;
import application.service.ShowService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import domain.exceptions.EventNotFoundException;
import domain.exceptions.RaffleException;
import domain.exceptions.ShowNotFoundException;
import domain.exceptions.UnauthorizedException;
import domain.exceptions.VenueMapNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import presentation.CurrentUser;
import presentation.Result;

import java.util.List;

@Component
@VaadinSessionScope
public class EventDetailPresenter {

    private static final Logger logger = LoggerFactory.getLogger(EventDetailPresenter.class);

    private final EventService eventService;
    private final ShowService showService;
    private final CurrentUser currentUser;

    public EventDetailPresenter(EventService eventService, ShowService showService, CurrentUser currentUser) {
        this.eventService = eventService;
        this.showService = showService;
        this.currentUser = currentUser;
    }

    /** The caller's raffle status for an event (also triggers the lazy draw once the sale has opened). */
    public Result<RaffleStatusDTO> getRaffleStatus(String eventId) {
        try {
            return new Result.Ok<>(eventService.getRaffleStatus(eventId, currentUser.getToken()));
        } catch (UnauthorizedException e) {
            return new Result.Fail<>("Please log in to view raffle details.");
        } catch (EventNotFoundException e) {
            return new Result.Fail<>("Event not found.");
        } catch (Exception e) {
            logger.error("Unexpected error loading raffle status for event id={}", eventId, e);
            return new Result.Fail<>("Failed to load raffle status.");
        }
    }

    public Result<Void> registerForRaffle(String eventId) {
        try {
            eventService.registerForRaffle(eventId, currentUser.getToken());
            return new Result.Ok<>(null);
        } catch (UnauthorizedException e) {
            return new Result.Fail<>("Please log in to register for the raffle.");
        } catch (EventNotFoundException e) {
            return new Result.Fail<>("Event not found.");
        } catch (RaffleException e) {
            return new Result.Fail<>(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error registering for raffle on event id={}", eventId, e);
            return new Result.Fail<>("Failed to register for the raffle.");
        }
    }

    public Result<EventDTO> getEvent(String eventId) {
        try {
            return new Result.Ok<>(eventService.getEventById(eventId));
        } catch (EventNotFoundException e) {
            return new Result.Fail<>("Event not found.");
        } catch (Exception e) {
            logger.error("Unexpected error loading event id={}", eventId, e);
            return new Result.Fail<>("Failed to load event.");
        }
    }

    public Result<List<ShowDTO>> getShowsForEvent(String eventId) {
        try {
            return new Result.Ok<>(showService.getShowsByEvent(eventId));
        } catch (Exception e) {
            logger.error("Unexpected error loading shows for event id={}", eventId, e);
            return new Result.Fail<>("Failed to load shows.");
        }
    }

    public Result<VenueMapDTO> getShowMap(String showId) {
        try {
            return new Result.Ok<>(showService.getHallLayout(showId));
        } catch (ShowNotFoundException e) {
            return new Result.Fail<>("Show not found.");
        } catch (VenueMapNotFoundException e) {
            return new Result.Fail<>("No venue map defined for this show.");
        } catch (Exception e) {
            logger.error("Unexpected error loading show map for show id={}", showId, e);
            return new Result.Fail<>("Failed to load show map.");
        }
    }
}
