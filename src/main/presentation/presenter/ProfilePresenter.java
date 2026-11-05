package presentation.presenter;

import application.dto.MemberDTO;
import application.service.MemberService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import domain.exceptions.SamePasswordException;
import domain.exceptions.UnauthorizedException;
import domain.exceptions.UsernameAlreadyTakenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import presentation.CurrentUser;
import presentation.Result;

@Component
@VaadinSessionScope
public class ProfilePresenter {

    private static final Logger logger = LoggerFactory.getLogger(ProfilePresenter.class);

    private final MemberService memberService;
    private final CurrentUser currentUser;

    public ProfilePresenter(MemberService memberService, CurrentUser currentUser) {
        this.memberService = memberService;
        this.currentUser = currentUser;
    }

    public Result<MemberDTO> loadProfile() {
        try {
            return Result.ok(memberService.getPersonalDetails(currentUser.getToken()));
        } catch (UnauthorizedException e) {
            return Result.fail("Your session has expired. Please log in again.");
        } catch (Exception e) {
            logger.warn("Failed to load profile for {}: {}", currentUser.getEmail(), e.getMessage());
            return Result.fail("Could not load your profile.");
        }
    }

    public Result<Void> changeUsername(String newUsername) {
        try {
            memberService.editUsername(currentUser.getToken(), newUsername);
            // Refresh the session-cached profile so HomeView/read-only displays update immediately.
            currentUser.setMember(MemberDTO.builder()
                    .username(newUsername)
                    .email(currentUser.getEmail())
                    .build());
            return Result.ok(null);
        } catch (UsernameAlreadyTakenException e) {
            return Result.fail("That username is already taken.");
        } catch (UnauthorizedException e) {
            return Result.fail("Your session has expired. Please log in again.");
        } catch (Exception e) {
            logger.warn("Failed to update username for {}: {}", currentUser.getEmail(), e.getMessage());
            return Result.fail("Could not update your username.");
        }
    }

    public Result<Void> changePassword(String newPassword) {
        try {
            memberService.editPassword(currentUser.getToken(), newPassword);
            return Result.ok(null);
        } catch (SamePasswordException e) {
            return Result.fail("New password must be different from your current password.");
        } catch (UnauthorizedException e) {
            return Result.fail("Your session has expired. Please log in again.");
        } catch (Exception e) {
            logger.warn("Failed to update password for {}: {}", currentUser.getEmail(), e.getMessage());
            return Result.fail("Could not update your password.");
        }
    }
}
