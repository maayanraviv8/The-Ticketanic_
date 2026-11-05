package application.service;

import application.interfaces.INotificationService;
import application.interfaces.INotifier;
import domain.Aggregates.notification.AppointmentNotification;
import domain.Aggregates.notification.Notification;
import domain.Aggregates.productioncompany.Permissions;
import domain.Irepository.INotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationManager implements INotificationService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationManager.class);

    private final INotificationRepository notificationRepo;
    private final INotifier notifier;

    public NotificationManager(INotificationRepository notificationRepo, INotifier notifier) {
        this.notificationRepo = notificationRepo;
        this.notifier = notifier;
    }

    @Override
    public void registerCurrentSession(String memberEmail) {
        notifier.register(memberEmail);
        logger.info("Member '{}' registered for live notifications.", memberEmail);
    }

    @Override
    public void unregisterCurrentSession(String memberEmail) {
        notifier.unregister(memberEmail);
        logger.info("Member '{}' unregistered from current session.", memberEmail);
    }

    @Override
    public String sendNotification(String memberEmail, Notification notification) {
        boolean delivered = notifier.notifyUser(memberEmail, notification);
        if (delivered) {
            logger.info("Notification '{}' delivered live to '{}'.", notification.getId(), memberEmail);
            return null;
        }
        notificationRepo.store(notification);
        logger.info("Notification '{}' queued for offline member '{}'.", notification.getId(), memberEmail);
        return notification.getId();
    }

    @Override
    public void sendNotification(String memberEmail, String subject, String content, String companyID) {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID().toString())
                .recipientEmail(memberEmail)
                .subject(subject)
                .content(content)
                .companyID(companyID)
                .build();
        sendNotification(memberEmail, notification);
    }

    @Override
    public void sendAppointment(String recipientEmail, String companyID, String appointerID, String subject, Set<Permissions> permissions) {
        AppointmentNotification notification = AppointmentNotification.builder()
                .id(UUID.randomUUID().toString())
                .recipientEmail(recipientEmail)
                .subject(subject)
                .content("You have been invited to join " + companyID + " as a " + (permissions != null ? "manager" : "owner") + ". Accept or decline below.")
                .companyID(companyID)
                .appointerID(appointerID)
                .permissions(permissions)
                .build();
        sendNotification(recipientEmail, notification);
    }

    @Override
    public List<Notification> getPendingNotifications(String memberEmail) {
        return notificationRepo.findByRecipientEmail(memberEmail);
    }

    @Override
    public void removeNotification(String id) {
        notificationRepo.delete(id);
        logger.info("Notification '{}' removed.", id);
    }

}
