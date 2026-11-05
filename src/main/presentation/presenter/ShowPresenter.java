package presentation.presenter;

import application.dto.ShowDTO;
import application.dto.VenueMapDTO;
import application.service.ShowService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import domain.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import presentation.CurrentUser;
import presentation.Result;

import java.util.List;

@Component
@VaadinSessionScope
public class ShowPresenter {

    private static final Logger logger = LoggerFactory.getLogger(ShowPresenter.class);

    private final ShowService showService;
    private final CurrentUser currentUser;

    public ShowPresenter(ShowService showService, CurrentUser currentUser) {
        this.showService = showService;
        this.currentUser = currentUser;
    }

    public Result<ShowDTO> createShow(String eventId, ShowDTO dto) {
        try {
            return Result.ok(showService.createShow(eventId, dto, currentUser.getToken()));
        } catch (UnauthorizedException e) {
            return Result.fail("You are not authorized to create shows.");
        } catch (DuplicateShowException e) {
            return Result.fail("A show on this date already exists for this event.");
        } catch (InvalidShowException | IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (DataAccessException e) {
            logger.error("Database error creating show for event '{}'", eventId, e);
            return Result.fail("A database error occurred. Please try again in a moment.");
        } catch (RuntimeException e) {
            logger.error("Failed to create show for event '{}'", eventId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<ShowDTO> editShow(String showId, ShowDTO dto) {
        try {
            return Result.ok(showService.editShow(showId, dto, currentUser.getToken()));
        } catch (UnauthorizedException e) {
            return Result.fail("You are not authorized to edit this show.");
        } catch (ShowNotFoundException e) {
            return Result.fail("Show not found.");
        } catch (InvalidShowException | IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (DataAccessException e) {
            logger.error("Database error editing show '{}'", showId, e);
            return Result.fail("A database error occurred. Please try again in a moment.");
        } catch (RuntimeException e) {
            logger.error("Failed to edit show '{}'", showId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> removeShow(String showId) {
        try {
            showService.removeShow(showId, currentUser.getToken());
            return Result.ok(null);
        } catch (UnauthorizedException e) {
            return Result.fail("You are not authorized to remove this show.");
        } catch (ShowNotFoundException e) {
            return Result.fail("Show not found.");
        } catch (DataAccessException e) {
            logger.error("Database error removing show '{}'", showId, e);
            return Result.fail("A database error occurred. Please try again in a moment.");
        } catch (RuntimeException e) {
            logger.error("Failed to remove show '{}'", showId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<List<ShowDTO>> getShowsByEvent(String eventId) {
        try {
            return Result.ok(showService.getShowsByEvent(eventId));
        } catch (RuntimeException e) {
            logger.error("Failed to load shows for event '{}'", eventId, e);
            return Result.fail("Failed to load shows.");
        }
    }

    public Result<VenueMapDTO> getHallLayout(String showId) {
        try {
            return Result.ok(showService.getHallLayout(showId));
        } catch (VenueMapNotFoundException e) {
            return Result.fail("No hall layout defined for this show.");
        } catch (RuntimeException e) {
            logger.error("Failed to load hall layout for show '{}'", showId, e);
            return Result.fail("Failed to load hall layout.");
        }
    }

    public Result<VenueMapDTO> defineHallLayout(String showId, VenueMapDTO venueMapDTO) {
        try {
            return Result.ok(showService.defineHallLayout(showId, venueMapDTO, currentUser.getToken()));
        } catch (UnauthorizedException e) {
            return Result.fail("You are not authorized to define hall layouts.");
        } catch (ShowNotFoundException e) {
            return Result.fail("Show not found.");
        } catch (VenueMapAlreadyExistsException e) {
            return Result.fail("A hall layout already exists for this show. Use edit instead.");
        } catch (InvalidShowException e) {
            return Result.fail(e.getMessage());
        } catch (DataAccessException e) {
            logger.error("Database error defining hall layout for show '{}'", showId, e);
            return Result.fail("A database error occurred. Please try again in a moment.");
        } catch (RuntimeException e) {
            logger.error("Failed to define hall layout for show '{}'", showId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<VenueMapDTO> updateHallLayout(String showId, VenueMapDTO venueMapDTO) {
        try {
            return Result.ok(showService.updateHallLayout(showId, venueMapDTO, currentUser.getToken()));
        } catch (UnauthorizedException e) {
            return Result.fail("You are not authorized to update hall layouts.");
        } catch (ShowNotFoundException e) {
            return Result.fail("Show not found.");
        } catch (VenueMapNotFoundException | InvalidShowException e) {
            return Result.fail(e.getMessage());
        } catch (DataAccessException e) {
            logger.error("Database error updating hall layout for show '{}'", showId, e);
            return Result.fail("A database error occurred. Please try again in a moment.");
        } catch (RuntimeException e) {
            logger.error("Failed to update hall layout for show '{}'", showId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> removeHallLayout(String showId) {
        try {
            showService.removeHallLayout(showId, currentUser.getToken());
            return Result.ok(null);
        } catch (UnauthorizedException e) {
            return Result.fail("You are not authorized to remove hall layouts.");
        } catch (VenueMapNotFoundException e) {
            return Result.fail("No hall layout exists for this show.");
        } catch (InvalidShowException e) {
            return Result.fail(e.getMessage());
        } catch (DataAccessException e) {
            logger.error("Database error removing hall layout for show '{}'", showId, e);
            return Result.fail("A database error occurred. Please try again in a moment.");
        } catch (RuntimeException e) {
            logger.error("Failed to remove hall layout for show '{}'", showId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }
}