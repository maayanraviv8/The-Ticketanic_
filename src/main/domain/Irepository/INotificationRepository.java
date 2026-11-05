package domain.Irepository;

import domain.Aggregates.notification.Notification;

public interface INotificationRepository extends IRepository<Notification> {
    java.util.List<Notification> findByRecipientEmail(String email);
}
