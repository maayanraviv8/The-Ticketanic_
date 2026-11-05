package presentation.presenter;

import application.dto.ProductionCompanyDTO;
import application.dto.RoleDTO;
import application.service.ProductionCompanyService;
import application.service.RoleService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import presentation.CurrentUser;
import presentation.Result;

import java.util.Map;

@Component
@VaadinSessionScope
public class OpenCompanyPresenter {

    private static final Logger logger = LoggerFactory.getLogger(OpenCompanyPresenter.class);

    private final ProductionCompanyService companyService;
    private final RoleService roleService;
    private final CurrentUser currentUser;

    public OpenCompanyPresenter(ProductionCompanyService companyService,
                                RoleService roleService,
                                CurrentUser currentUser) {
        this.companyService = companyService;
        this.roleService = roleService;
        this.currentUser = currentUser;
    }

    public Result<ProductionCompanyDTO> openCompany(String companyName) {
        try {
            ProductionCompanyDTO dto = companyService.openProductionCompany(companyName, currentUser.getToken());
            refreshUserRoles();
            return Result.ok(dto);
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to open company '{}': {}", companyName, e.getMessage());
            return Result.fail(e.getMessage());
        } catch (DataAccessException e) {
            logger.error("Database error opening company '{}'", companyName, e);
            return Result.fail("A database error occurred. Please try again in a moment.");
        } catch (RuntimeException e) {
            logger.error("Unexpected error opening company '{}'", companyName, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }

    private void refreshUserRoles() {
        if (!currentUser.isLoggedIn()) return;
        try {
            Map<String, RoleDTO> freshRoles = roleService.getUserRoles(currentUser.getEmail());
            currentUser.setRolesByCompany(freshRoles);
        } catch (RuntimeException e) {
            logger.warn("Could not refresh user roles for '{}'", currentUser.getEmail(), e);
        }
    }
}
