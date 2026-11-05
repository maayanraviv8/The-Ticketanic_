package presentation.presenter;

import application.dto.LoginContextDTO;
import application.dto.RoleDTO;
import application.service.ActiveOrderService;
import application.service.AdminService;
import application.service.MemberService;
import application.service.RoleService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import domain.exceptions.AccountSuspendedException;
import domain.exceptions.InvalidCredentialsException;
import domain.exceptions.MemberNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import presentation.CurrentUser;
import presentation.Result;

import java.util.Map;

@Component
@VaadinSessionScope
public class LoginPresenter {

    private static final Logger logger = LoggerFactory.getLogger(LoginPresenter.class);

    private final MemberService memberService;
    private final CurrentUser currentUser;
    private final AdminService adminService;
    private final RoleService roleService;
    private final ActiveOrderService activeOrderService;

    public LoginPresenter(MemberService memberService, CurrentUser currentUser, AdminService adminService,
                          RoleService roleService, ActiveOrderService activeOrderService) {
        this.memberService = memberService;
        this.currentUser = currentUser;
        this.adminService = adminService;
        this.roleService = roleService;
        this.activeOrderService = activeOrderService;
    }

    public Result<LoginContextDTO> login(String email, String password) {
        try {
            LoginContextDTO context = memberService.login(email, password);
            currentUser.login(context);
            Map<String, RoleDTO> roles = roleService.getUserRoles(email);
            currentUser.setRolesByCompany(roles);
            currentUser.setAdmin(adminService.isAdmin(email));
            currentUser.setActiveOrderId(activeOrderService.getActiveOrderIdForMember(context.getToken()));
            return Result.ok(context);
        } catch (MemberNotFoundException | InvalidCredentialsException e) {
            return Result.fail("Invalid email or password");
        } catch (AccountSuspendedException e) {
            return Result.fail("This account has been suspended");
        } catch (DataAccessException e) {
            logger.error("Database error during login for email '{}'", email, e);
            return Result.fail("A database error occurred. Please try again in a moment.");
        } catch (RuntimeException e) {
            logger.error("Unexpected error during login for email '{}'", email, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }
}
