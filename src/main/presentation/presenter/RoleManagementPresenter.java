package presentation.presenter;

import application.dto.RoleDTO;
import application.service.ProductionCompanyService;
import application.service.RoleService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import domain.Aggregates.productioncompany.Permissions;
import domain.exceptions.AppointmentPermissionException;
import domain.exceptions.MemberHasRoleException;
import domain.exceptions.MemberNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import presentation.CurrentUser;
import presentation.Result;

import java.util.List;
import java.util.Set;

@Component
@VaadinSessionScope
public class RoleManagementPresenter {

    private static final Logger logger = LoggerFactory.getLogger(RoleManagementPresenter.class);

    private final RoleService roleService;
    private final ProductionCompanyService companyService;
    private final CurrentUser currentUser;

    public RoleManagementPresenter(RoleService roleService, ProductionCompanyService companyService, CurrentUser currentUser) {
        this.roleService = roleService;
        this.companyService = companyService;
        this.currentUser = currentUser;
    }

    public Result<List<RoleDTO>> getRoles(String companyId) {
        try {
            List<RoleDTO> roles = companyService.getCompanyRoles(companyId);
            return Result.ok(roles);
        } catch (Exception e) {
            logger.error("Failed to get roles for company '{}'", companyId, e);
            return Result.fail("Failed to load roles");
        }
    }

    public Result<Void> appointOwner(String companyId, String newOwnerEmail) {
        try {
            roleService.appointOwner(currentUser.getToken(), companyId, newOwnerEmail);
            return Result.ok(null);
        }catch (AppointmentPermissionException | MemberNotFoundException | MemberHasRoleException e){
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to appoint owner '{}' in company '{}'", newOwnerEmail, companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> appointManager(String companyId, String newManagerEmail, Set<Permissions> permissions) {
        try {
            roleService.appointManager(currentUser.getToken(), companyId, newManagerEmail, permissions);
            return Result.ok(null);
        } catch (AppointmentPermissionException | MemberNotFoundException | MemberHasRoleException e){
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to appoint manager '{}' in company '{}'", newManagerEmail, companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> removeAppointment(String companyId, String memberEmail) {
        try {
            roleService.removeAppointment(currentUser.getToken(), companyId, memberEmail);
            return Result.ok(null);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
        catch (Exception e) {
            logger.error("Failed to remove appointment of '{}' in company '{}'", memberEmail, companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> addManagerPermissions(String companyId, String memberEmail, Set<Permissions> permissions) {
        try {
            roleService.addManagerPermission(currentUser.getToken(), companyId, memberEmail, permissions);
            return Result.ok(null);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }catch (Exception e) {
            logger.error("Failed to remove appointment of '{}' in company '{}'", memberEmail, companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    public Result<Void> removeManagerPermissions(String companyId, String memberEmail, Set<Permissions> permissions) {
        try {
            roleService.removeManagerPermission(currentUser.getToken(), companyId, memberEmail, permissions);
            return Result.ok(null);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }catch (Exception e) {
            logger.error("Failed to remove appointment of '{}' in company '{}'", memberEmail, companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }
}