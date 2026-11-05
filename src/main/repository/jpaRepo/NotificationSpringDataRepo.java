package repository.jpaRepo;

import domain.Aggregates.notification.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationSpringDataRepo extends JpaRepository<Notification, String> {

    List<Notification> findByRecipientEmail(String recipientEmail);
}