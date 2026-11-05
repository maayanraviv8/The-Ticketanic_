package presentation.presenter;

import application.dto.EventDTO;
import application.dto.OrderDTO;
import application.dto.ProductionCompanyDTO;
import application.dto.RoleDTO;
import application.service.EventService;
import application.service.ProductionCompanyService;
import application.service.RoleService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import domain.exceptions.CompanyNotFoundException;
import domain.exceptions.EventNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import presentation.CurrentUser;
import presentation.Result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@VaadinSessionScope
public class CompanyDashboardPresenter {

    private static final Logger logger = LoggerFactory.getLogger(CompanyDashboardPresenter.class);

    private final ProductionCompanyService companyService;
    private final EventService eventService;
    private final RoleService roleService;
    private final CurrentUser currentUser;

    public CompanyDashboardPresenter(ProductionCompanyService companyService, EventService eventService, RoleService roleService, CurrentUser currentUser) {
        this.companyService = companyService;
        this.eventService = eventService;
        this.roleService = roleService;
        this.currentUser = currentUser;
    }

    public Result<ProductionCompanyDTO> getCompanyInfo(String companyId) {
        try {
            return Result.ok(companyService.getCompanyInfo(companyId));
        } catch (CompanyNotFoundException | IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (RuntimeException e) {
            logger.error("Unexpected error loading company '{}'", companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<List<EventDTO>> getCompanyEvents(String companyId) {
        try {
            List<EventDTO> events = eventService.getEventsByProductionCompany(companyId);
            return Result.ok(events);
        } catch (EventNotFoundException | IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (RuntimeException e) {
            logger.error("Unexpected error loading events for company '{}'", companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public void refreshUserRoles() {
        if (!currentUser.isLoggedIn()) return;
        try {
            String memberEmail = currentUser.getMember().getEmail();
            Map<String, RoleDTO> freshRoles = roleService.getUserRoles(memberEmail);
            currentUser.setRolesByCompany(freshRoles);
        } catch (RuntimeException e) {
            logger.warn("Could not refresh user roles for '{}'",
                    currentUser.getMember().getEmail(), e);
        }
    }

    public Result<Void> closeCompany(String companyId) {
        try {
            companyService.suspendProductionCompany(companyId, currentUser.getToken());
            return Result.ok(null);
        } catch (Exception e) {
            logger.error("Failed to close company '{}'", companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> reopenCompany(String companyId) {
        try {
            companyService.reopenProductionCompany(companyId, currentUser.getToken());
            return Result.ok(null);
        } catch (Exception e) {
            logger.error("Failed to reopen company '{}'", companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Map<String, List<String>>> getCompanyHierarchy(String companyId) {
        try {
            Map<String, List<String>> hierarchy = companyService.getAllRoles(companyId, currentUser.getToken());
            return Result.ok(hierarchy);
        } catch (Exception e) {
            logger.error("Failed to load hierarchy for company '{}'", companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Map<String, List<OrderDTO>>> getCompanyPurchaseHistory(String companyId) {
        try {
            List<OrderDTO> orders = eventService.getPurchaseHistoryByProductionCompany(companyId, null, null, currentUser.getToken());
            Map<String, List<OrderDTO>> grouped = new HashMap<>();
            for (OrderDTO order : orders) {
                grouped.computeIfAbsent(companyService.getEventName(order.getEventId()), k -> new ArrayList<>()).add(order);
            }
            return Result.ok(grouped);
        } catch (Exception e) {
            logger.error("Failed to load purchase history for company '{}'", companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> relinquishOwnership(String companyId) {
        try {
            roleService.relinquishOwnership(currentUser.getToken(), companyId);
            return Result.ok(null);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to relinquish ownership of '{}'", companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }
}