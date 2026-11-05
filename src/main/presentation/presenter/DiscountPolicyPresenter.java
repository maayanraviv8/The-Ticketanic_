package presentation.presenter;

import application.dto.DiscountPolicyDTO;
import application.dto.EventDTO;
import application.service.EventService;
import application.service.ProductionCompanyService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import presentation.CurrentUser;
import presentation.Result;

import java.util.List;

@Component
@VaadinSessionScope
public class DiscountPolicyPresenter {

    private static final Logger logger = LoggerFactory.getLogger(CompanyDashboardPresenter.class);

    private final ProductionCompanyService companyService;
    private final EventService eventService;
    private final CurrentUser currentUser;

    public DiscountPolicyPresenter(ProductionCompanyService companyService, EventService eventService, CurrentUser currentUser) {
        this.companyService = companyService;
        this.eventService = eventService;
        this.currentUser = currentUser;
    }

    public Result<Void> setCompanyPolicy(String companyId, DiscountPolicyDTO dto) {
        try {
            companyService.setCompanyDiscountPolicy(currentUser.getToken(), companyId, dto);
            return Result.ok(null);
        } catch (Exception e) {
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> removeCompanyPolicy(String companyId) {
        return setCompanyPolicy(companyId, null);
    }

    public Result<DiscountPolicyDTO> getCompanyPolicy(String companyId) {
        try {
            return Result.ok(companyService.getDiscountPolicy(companyId));
        } catch (IllegalArgumentException e){
            return Result.fail(e.getMessage());
        }
        catch (Exception e) {
            logger.error("Unexpected error loading discount policy for company '{}'", companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> setEventPolicy(String eventId, DiscountPolicyDTO dto) {
        try {
            eventService.setDiscountPolicy(eventId, dto, currentUser.getToken());
            return Result.ok(null);
        } catch (Exception e) {
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> removeEventPolicy(String eventId) {
        return setEventPolicy(eventId, null);
    }

    public Result<DiscountPolicyDTO> getEventPolicy(String eventId) {
        try {
            return Result.ok(eventService.getDiscountPolicy(eventId));
        } catch (Exception e) {
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<List<EventDTO>> getCompanyEvents(String companyId) {
        try {
            List<EventDTO> events = eventService.getEventsByProductionCompany(companyId);
            return Result.ok(events);
        } catch (Exception e) {
            logger.error("Unexpected error loading events for company '{}'", companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

}