package repository.memoryRepo;

import domain.Aggregates.notification.Notification;
import domain.Irepository.INotificationRepository;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;


@Profile({"dev", "test"})
@Repository
public class NotificationMemoryRepo implements INotificationRepository {
    private static final Logger logger = LoggerFactory.getLogger(NotificationMemoryRepo.class);

    private final ConcurrentHashMap<String, Notification> notifications = new ConcurrentHashMap<>();

    @Override
    public synchronized void store(Notification entity) {
        if (notifications.containsKey(entity.getId()))
            throw new IllegalArgumentException("Notification '" + entity.getId() + "' already exists.");
        notifications.put(entity.getId(), entity);
        logger.info("Notification '{}' created.", entity.getId());
    }

    @Override
    public synchronized void delete(String id) {
        if (!notifications.containsKey(id))
            throw new IllegalArgumentException("Notification not found or already deleted.");
        notifications.remove(id);
        logger.info("Notification '{}' deleted.", id);
    }

    @Override
    public Notification findById(String id) {
        return notifications.get(id);
    }

    @Override
    public List<Notification> getAll() {
        return new ArrayList<>(notifications.values());
    }

    @Override
    public List<Notification> findByRecipientEmail(String email) {
        return notifications.values().stream()
                .filter(n -> n.getRecipientEmail().equals(email))
                .toList();
    }
}
