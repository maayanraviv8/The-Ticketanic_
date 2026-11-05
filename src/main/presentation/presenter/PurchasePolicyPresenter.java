package presentation.presenter;

import application.dto.EventDTO;
import application.dto.PurchasePolicyDTO;
import application.service.EventService;
import application.service.ProductionCompanyService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import domain.exceptions.EventNotFoundException;
import domain.exceptions.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import presentation.CurrentUser;
import presentation.Result;

import java.util.List;

@Component
@VaadinSessionScope
public class PurchasePolicyPresenter {

    private static final Logger logger = LoggerFactory.getLogger(PurchasePolicyPresenter.class);

    public static final String TYPE_MIN_AGE     = "MIN_AGE";
    public static final String TYPE_MIN_TICKETS = "MIN_TICKETS";
    public static final String TYPE_MAX_TICKETS = "MAX_TICKETS";
    public static final String TYPE_AND         = "AND";
    public static final String TYPE_OR          = "OR";

    private final ProductionCompanyService companyService;
    private final EventService             eventService;
    private final CurrentUser currentUser;

    public PurchasePolicyPresenter(ProductionCompanyService companyService,
                                   EventService eventService,
                                   CurrentUser currentUser) {
        this.companyService = companyService;
        this.eventService   = eventService;
        this.currentUser    = currentUser;
    }


    public Result<PurchasePolicyDTO> getCompanyPolicy(String companyId) {
        try {
            PurchasePolicyDTO dto = companyService.getPurchasePolicy(companyId);
            return Result.ok(isEffectivelyEmpty(dto) ? null : dto);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
        catch (Exception e) {
            logger.error("Failed to load purchase policy for company '{}'", companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> setCompanyPolicy(String companyId, PurchasePolicyDTO dto) {
        try {
            companyService.setCompanyPurchasePolicy(currentUser.getToken(), companyId, dto);
            return Result.ok(null);
        } catch (UnauthorizedException | IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
        catch (Exception e) {
            logger.error("Failed to set purchase policy for company '{}'", companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> removeCompanyPolicy(String companyId) {
        return setCompanyPolicy(companyId, null);
    }


    public Result<PurchasePolicyDTO> getEventPolicy(String eventId) {
        try {
            PurchasePolicyDTO dto = eventService.getEventPurchasePolicy(eventId);
            return Result.ok(isEffectivelyEmpty(dto) ? null : dto);
        }catch (EventNotFoundException e) {
            return Result.fail(e.getMessage());
        }
        catch (Exception e) {
            logger.error("Failed to load purchase policy for event '{}'", eventId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> setEventPolicy(String eventId, PurchasePolicyDTO dto) {
        try {
            eventService.setEventPurchasePolicy(eventId, dto, currentUser.getToken());
            return Result.ok(null);
        }catch (UnauthorizedException | EventNotFoundException | IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
        catch (Exception e) {
            logger.error("Failed to set purchase policy for event '{}'", eventId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> removeEventPolicy(String eventId) {
        return setEventPolicy(eventId, null);
    }


    public Result<List<EventDTO>> getCompanyEvents(String companyId) {
        try {
            return Result.ok(eventService.getEventsByProductionCompany(companyId));
        } catch (Exception e) {
            logger.error("Failed to load events for company '{}'", companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    private boolean isEffectivelyEmpty(PurchasePolicyDTO dto) {
        if (dto == null || "NONE".equals(dto.type)) return true;
        return (TYPE_AND.equals(dto.type) || TYPE_OR.equals(dto.type))
                && (dto.children == null || dto.children.isEmpty());
    }
}