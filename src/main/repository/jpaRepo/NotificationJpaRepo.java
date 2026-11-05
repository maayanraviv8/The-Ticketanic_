package repository.jpaRepo;

import domain.Aggregates.notification.Notification;
import domain.Irepository.INotificationRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Repository
@Profile("cloud")
public class NotificationJpaRepo implements INotificationRepository {
    private static final Logger logger = LoggerFactory.getLogger(NotificationJpaRepo.class);

    private final NotificationSpringDataRepo springRepo;

    public NotificationJpaRepo(NotificationSpringDataRepo springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public void store(Notification entity) {
        if (springRepo.existsById(entity.getId()))
            throw new IllegalArgumentException("Notification '" + entity.getId() + "' already exists.");
        springRepo.save(entity);
        logger.info("Notification '{}' created.", entity.getId());
    }

    @Override
    public void delete(String id) {
        if (!springRepo.existsById(id))
            throw new IllegalArgumentException("Notification not found or already deleted.");
        springRepo.deleteById(id);
        logger.info("Notification '{}' deleted.", id);

    }

    @Override
    public Notification findById(String id) {
        return springRepo.findById(id).orElse(null);
    }

    @Override
    public List<Notification> getAll() {
        return springRepo.findAll();
    }

    @Override
    public List<Notification> findByRecipientEmail(String email) {
        return springRepo.findByRecipientEmail(email);
    }
}