package presentation.presenter;

import application.dto.DiscountPolicyDTO;
import application.dto.EventDTO;
import application.dto.PurchasePolicyDTO;
import application.dto.ShowDTO;
import application.dto.VenueMapDTO;
import java.util.List;
import application.service.EventService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import domain.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import presentation.CurrentUser;
import presentation.Result;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@VaadinSessionScope
public class EventManagementPresenter {

    private static final Logger logger = LoggerFactory.getLogger(EventManagementPresenter.class);

    private final EventService eventService;
    private final ShowPresenter showPresenter;
    private final CurrentUser currentUser;

    public EventManagementPresenter(EventService eventService, ShowPresenter showPresenter, CurrentUser currentUser) {
        this.eventService = eventService;
        this.showPresenter = showPresenter;
        this.currentUser = currentUser;
    }

    //events

    public Result<List<EventDTO>> getCompanyEvents(String companyId) {
        try {
            return Result.ok(eventService.getEventsByProductionCompany(companyId));
        } catch (RuntimeException e) {
            logger.error("Failed to load events for company '{}'", companyId, e);
            return Result.fail("Failed to load events.");
        }
    }

    public Result<EventDTO> createEvent(String companyId, EventDTO eventDTO) {
        try {
            return Result.ok(eventService.createEvent(eventDTO, companyId, currentUser.getToken()));
        } catch (UnauthorizedException e) {
            return Result.fail("You are not authorized to create events.");
        } catch (DuplicateEventException e) {
            return Result.fail("An event with this name already exists.");
        } catch (InvalidEventException e) {
            return Result.fail(e.getMessage());
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (DataAccessException e) {
            logger.error("Database error creating event for company '{}'", companyId, e);
            return Result.fail("A database error occurred. Please try again in a moment.");
        } catch (RuntimeException e) {
            logger.error("Failed to create event for company '{}'", companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<EventDTO> editEvent(String eventId, EventDTO eventDTO) {
        try {
            return Result.ok(eventService.editEvent(eventId, eventDTO, currentUser.getToken()));
        } catch (UnauthorizedException e) {
            return Result.fail("You are not authorized to edit this event.");
        } catch (EventNotFoundException e) {
            return Result.fail("Event not found.");
        } catch (InvalidEventException e) {
            return Result.fail(e.getMessage());
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (DataAccessException e) {
            logger.error("Database error editing event '{}'", eventId, e);
            return Result.fail("A database error occurred. Please try again in a moment.");
        } catch (RuntimeException e) {
            logger.error("Failed to edit event '{}'", eventId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> configureRaffle(String eventId, LocalDateTime registrationDeadline,
                                        Duration codeValidity, int numberOfWinners) {
        try {
            eventService.configureRaffle(eventId, registrationDeadline, codeValidity, numberOfWinners,
                    currentUser.getToken());
            return Result.ok(null);
        } catch (UnauthorizedException e) {
            return Result.fail("You are not authorized to configure a raffle.");
        } catch (EventNotFoundException e) {
            return Result.fail("Event not found.");
        } catch (InvalidEventException | IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (DataAccessException e) {
            logger.error("Database error configuring raffle for event '{}'", eventId, e);
            return Result.fail("A database error occurred. Please try again in a moment.");
        } catch (RuntimeException e) {
            logger.error("Failed to configure raffle for event '{}'", eventId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> setEventPurchasePolicy(String eventId, PurchasePolicyDTO dto) {
        try {
            eventService.setEventPurchasePolicy(eventId, dto, currentUser.getToken());
            return Result.ok(null);
        } catch (UnauthorizedException e) {
            return Result.fail("You are not authorized to set purchase policies.");
        } catch (EventNotFoundException e) {
            return Result.fail("Event not found.");
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (DataAccessException e) {
            logger.error("Database error setting purchase policy for event '{}'", eventId, e);
            return Result.fail("A database error occurred. Please try again in a moment.");
        } catch (RuntimeException e) {
            logger.error("Failed to set purchase policy for event '{}'", eventId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> setEventDiscountPolicy(String eventId, DiscountPolicyDTO dto) {
        try {
            eventService.setDiscountPolicy(eventId, dto, currentUser.getToken());
            return Result.ok(null);
        } catch (UnauthorizedException e) {
            return Result.fail("You are not authorized to set discount policies.");
        } catch (EventNotFoundException e) {
            return Result.fail("Event not found.");
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (DataAccessException e) {
            logger.error("Database error setting discount policy for event '{}'", eventId, e);
            return Result.fail("A database error occurred. Please try again in a moment.");
        } catch (RuntimeException e) {
            logger.error("Failed to set discount policy for event '{}'", eventId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> removeEvent(String eventId) {
        try {
            eventService.removeEvent(eventId, currentUser.getToken());
            return Result.ok(null);
        } catch (UnauthorizedException e) {
            return Result.fail("You are not authorized to remove this event.");
        } catch (EventNotFoundException e) {
            return Result.fail(e.getMessage());
        } catch (DataAccessException e) {
            logger.error("Database error removing event '{}'", eventId, e);
            return Result.fail("A database error occurred. Please try again in a moment.");
        } catch (RuntimeException e) {
            logger.error("Failed to remove event '{}'", eventId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }


    public Result<List<ShowDTO>> getShowsByEvent(String eventId) {
        return showPresenter.getShowsByEvent(eventId);
    }

    //Venue Map
    public Result<VenueMapDTO> getHallLayout(String showId) {
        return showPresenter.getHallLayout(showId);
    }

    public Result<VenueMapDTO> defineHallLayout(String showId, VenueMapDTO venueMapDTO) {
        return showPresenter.defineHallLayout(showId, venueMapDTO);
    }

    public Result<VenueMapDTO> updateHallLayout(String showId, VenueMapDTO venueMapDTO) {
        return showPresenter.updateHallLayout(showId, venueMapDTO);
    }

    public Result<Void> removeHallLayout(String showId) {
        return showPresenter.removeHallLayout(showId);
    }
}