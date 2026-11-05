package presentation.presenter;

import application.dto.OrderDTO;
import application.dto.ProductionCompanyDTO;
import application.dto.RoleDTO;
import application.dto.SuspensionDTO;
import application.service.AdminService;
import application.service.ProductionCompanyService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import domain.exceptions.AdminException;
import domain.exceptions.CompanyNotFoundException;
import domain.exceptions.MemberException;
import domain.exceptions.ProductionCompanyException;
import domain.exceptions.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import presentation.CurrentUser;
import presentation.Result;


import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@VaadinSessionScope
public class AdminPresenter {

    private static final Logger logger = LoggerFactory.getLogger(AdminPresenter.class);

    private final AdminService adminService;
    private final ProductionCompanyService companyService;
    private final CurrentUser currentUser;

    public AdminPresenter(AdminService adminService, ProductionCompanyService companyService, CurrentUser currentUser) {
        this.adminService = adminService;
        this.companyService = companyService;
        this.currentUser = currentUser;
    }

    public Result<List<ProductionCompanyDTO>> getAllCompanies() {
        try {
            return Result.ok(companyService.getAllCompanies());
        } catch (Exception e) {
            logger.error("Failed to load companies", e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<List<RoleDTO>> getCompanyHierarchy(String companyId) {
        try {
            return Result.ok(companyService.getCompanyRoles(companyId));
        }catch (CompanyNotFoundException e){
            return Result.fail(e.getMessage());
        }
        catch (Exception e) {
            logger.error("Failed to load hierarchy for company '{}'", companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Map<String, Map<String, List<OrderDTO>>>> getPurchaseHistory(String companyId) {
        try {
            Map<String, Map<String, List<OrderDTO>>> all =
                    adminService.getGlobalHistoryByCompaniesAndEvents(currentUser.getToken(), null, null);
            Map<String, List<OrderDTO>> forCompany = all.getOrDefault(companyId, Map.of());
            return Result.ok(Map.of(companyId, forCompany));
        } catch (Exception e) {
            logger.error("Failed to load purchase history for company '{}'", companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> closeCompany(String companyId) {
        try {
            adminService.closeProductionCompany(currentUser.getToken(), companyId);
            return Result.ok(null);
        } catch (AdminException | ProductionCompanyException | UnauthorizedException e) {
            return Result.fail(e.getMessage());
        }catch (Exception e) {
            logger.error("Failed to close company '{}'", companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> suspendMember(String email) {
        try {
            adminService.suspendMember(currentUser.getToken(), email);
            return Result.ok(null);
        } catch (AdminException | MemberException | UnauthorizedException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to suspend member '{}'", email, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> unsuspendMember(String email) {
        try {
            adminService.unsuspendMember(currentUser.getToken(), email);
            return Result.ok(null);
        } catch (AdminException | MemberException | UnauthorizedException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to unsuspend member '{}'", email, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> cancelMembership(String email) {
        try {
            adminService.cancelMembership(currentUser.getToken(), email);
            return Result.ok(null);
        } catch (AdminException | MemberException | UnauthorizedException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to cancel membership for '{}'", email, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<List<SuspensionDTO>> getMemberSuspensions(boolean activeOnly) {
        try {
            return Result.ok(adminService.getMemberSuspensions(currentUser.getToken(), activeOnly));
        } catch (AdminException | UnauthorizedException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to fetch member suspensions (activeOnly={})", activeOnly, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Map<String, List<OrderDTO>>> getPurchaseHistoryByBuyers(LocalDateTime start, LocalDateTime end) {
        try {
            return Result.ok(adminService.getGlobalHistoryByBuyers(currentUser.getToken(), start, end));
        } catch (AdminException | UnauthorizedException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to load purchase history by buyers", e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Map<String, Map<String, List<OrderDTO>>>> getPurchaseHistoryByCompanies(LocalDateTime start, LocalDateTime end) {
        try {
            Map<String, Map<String, List<OrderDTO>>> raw = adminService.getGlobalHistoryByCompaniesAndEvents(currentUser.getToken(), start, end);
            Map<String, Map<String, List<OrderDTO>>> named = new LinkedHashMap<>();
            for (var companyEntry : raw.entrySet()) {
                Map<String, List<OrderDTO>> eventMap = new LinkedHashMap<>();
                for (var eventEntry : companyEntry.getValue().entrySet()) {
                    eventMap.put(companyService.getEventName(eventEntry.getKey()), eventEntry.getValue());
                }
                named.put(companyEntry.getKey(), eventMap);
            }
            return Result.ok(named);
        } catch (AdminException | UnauthorizedException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to load purchase history by companies and events", e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }
}