package presentation.presenter;

import application.interfaces.INotificationService;
import application.interfaces.INotifier;
import application.interfaces.IRoleService;
import domain.Aggregates.notification.AppointmentNotification;
import domain.Aggregates.notification.Notification;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import presentation.CurrentUser;
import presentation.Result;

import java.util.List;

@Component
@VaadinSessionScope
public class NotificationsPresenter {

    private static final Logger logger = LoggerFactory.getLogger(NotificationsPresenter.class);

    private final INotificationService notificationService;
    private final IRoleService roleService;
    private final CurrentUser currentUser;

    public NotificationsPresenter(INotificationService notificationService,
                                  INotifier notifier,
                                  @Lazy IRoleService roleService,
                                  CurrentUser currentUser) {
        this.notificationService = notificationService;
        this.roleService = roleService;
        this.currentUser = currentUser;
        notifier.setAppointmentHandler((notification, accepted) -> {
            try {
                roleService.acceptAppointment(currentUser.getToken(), notification.getCompanyID(), accepted);
                currentUser.setRolesByCompany(roleService.getUserRoles(currentUser.getEmail()));
            } catch (Exception e) {
                logger.warn("Failed to process live appointment for {}: {}", currentUser.getEmail(), e.getMessage());
            }
        });
    }

    public Result<List<Notification>> getPendingNotifications() {
        try {
            List<Notification> notifications = notificationService.getPendingNotifications(currentUser.getEmail());
            return Result.ok(notifications != null ? notifications : List.of());
        } catch (Exception e) {
            logger.warn("Failed to load notifications for {}: {}", currentUser.getEmail(), e.getMessage());
            return Result.fail("Could not load notifications.");
        }
    }

    public Result<Void> dismiss(String notificationId) {
        try {
            notificationService.removeNotification(notificationId);
            return Result.ok(null);
        } catch (Exception e) {
            logger.warn("Failed to dismiss notification {}: {}", notificationId, e.getMessage());
            return Result.fail("Could not dismiss notification.");
        }
    }

    public Result<Void> respondToAppointment(AppointmentNotification notification, boolean accepted) {
        try {
            roleService.acceptAppointment(currentUser.getToken(), notification.getCompanyID(), accepted);
            currentUser.setRolesByCompany(roleService.getUserRoles(currentUser.getEmail()));
            return Result.ok(null);
        } catch (Exception e) {
            logger.warn("Failed to process appointment response for {}: {}", currentUser.getEmail(), e.getMessage());
            return Result.fail("Could not process appointment: " + e.getMessage());
        }
    }
}
