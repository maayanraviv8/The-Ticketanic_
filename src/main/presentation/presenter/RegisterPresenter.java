package presentation.presenter;

import application.service.MemberService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import domain.exceptions.EmailAlreadyRegisteredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import presentation.Result;

@Component
@VaadinSessionScope
public class RegisterPresenter {

    private static final Logger logger = LoggerFactory.getLogger(RegisterPresenter.class);

    private final MemberService memberService;

    public RegisterPresenter(MemberService memberService) {
        this.memberService = memberService;
    }

    /**
     * Registers a new member. The member stays logged out (UC2), so we deliberately
     * do not populate {@code CurrentUser} here — the view redirects to the login page.
     */
    public Result<Void> register(String username, String email, String password) {
        try {
            memberService.register(username, email, password);
            return Result.ok(null);
        } catch (EmailAlreadyRegisteredException e) {
            return Result.fail("An account with that email already exists");
        } catch (DataAccessException e) {
            logger.error("Database error during registration for email '{}'", email, e);
            return Result.fail("A database error occurred. Please try again in a moment.");
        } catch (RuntimeException e) {
            logger.error("Unexpected error during registration for email '{}'", email, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }
}
