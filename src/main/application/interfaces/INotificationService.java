package application.interfaces;

import domain.Aggregates.notification.Notification;
import domain.Aggregates.productioncompany.Permissions;

import java.util.List;
import java.util.Set;

public interface INotificationService {
    void registerCurrentSession(String memberEmail);
    void unregisterCurrentSession(String memberEmail);
    String sendNotification(String memberEmail, Notification notification);
    void sendNotification(String memberEmail, String subject, String content, String companyID);
    void sendAppointment(String recipientEmail, String companyID, String appointerID, String subject, Set<Permissions> permissions);
    List<Notification> getPendingNotifications(String memberEmail);
    void removeNotification(String id);
}
