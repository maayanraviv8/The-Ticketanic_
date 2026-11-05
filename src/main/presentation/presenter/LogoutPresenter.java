package presentation.presenter;

import application.service.MemberService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import presentation.CurrentUser;
import presentation.Result;

@Component
@VaadinSessionScope
public class LogoutPresenter {

    private static final Logger logger = LoggerFactory.getLogger(LogoutPresenter.class);

    private final MemberService memberService;
    private final CurrentUser currentUser;

    public LogoutPresenter(MemberService memberService, CurrentUser currentUser) {
        this.memberService = memberService;
        this.currentUser = currentUser;
    }

    public Result<Void> logout() {
        String token = currentUser.getToken();
        try {
            if (token != null) {
                memberService.logout(token);
            }
        } catch (RuntimeException e) {
            logger.error("Unexpected error during logout; clearing session anyway", e);
        } finally {
            currentUser.logout();
        }
        return Result.ok(null);
    }
}
