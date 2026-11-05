package repository.jpaRepo;

import domain.Aggregates.event.Event;
import domain.Irepository.IEventRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Repository
@Profile("cloud")
public class EventJpaRepo implements IEventRepository {
    private static final Logger logger = LoggerFactory.getLogger(EventJpaRepo.class);

    private final EventSpringDataRepo springRepo;

    public EventJpaRepo(EventSpringDataRepo springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public void store(Event entity) {
         boolean isNew = entity.getEventId() == null || entity.getEventId().isBlank();
        if (isNew) {
            entity.setEventId(java.util.UUID.randomUUID().toString());
            entity.setVersion(0L);
        }

        Event saved;
        try {
            saved = springRepo.saveAndFlush(entity);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            throw optimisticLockFailure(entity.getEventId(), entity.getVersion());
        } catch (jakarta.persistence.OptimisticLockException e) {
            throw optimisticLockFailure(entity.getEventId(), entity.getVersion());
        }

        if (isNew) {
            logger.info("Stored new event id={} name='{}'", saved.getEventId(), saved.getName());
        } else {
            logger.info("Updated event id={} to version={}", saved.getEventId(), saved.getVersion());
        }
    }
    @Override
    public void delete(String id) {
        if (!springRepo.existsById(id)) {
            logger.warn("Attempted to delete non-existent event id={}", id);
            throw new IllegalArgumentException("Event not found: " + id);
        }
        springRepo.deleteById(id);
        logger.info("Deleted event id={}", id);
    }

    private domain.exceptions.OptimisticLockException optimisticLockFailure(String eventId, long expectedVersion) {
        logger.warn("Optimistic locking conflict on event id={}: expected={}", eventId, expectedVersion);
        return new domain.exceptions.OptimisticLockException(
                "Concurrent modification detected for event id=" + eventId
                        + " (expected version=" + expectedVersion + ")");
    }

    @Override
    public Event findById(String id) {
        return springRepo.findById(id).orElse(null);
    }

    @Override
    public List<Event> getAll() {
        return springRepo.findAll();
    }

    @Override
    public List<Event> findByProductionCompanyId(String productionCompanyId) {
        // Assuming the ID matches the name conceptually in the system based on Event schema
        return springRepo.findByProductionCompanyName(productionCompanyId);
    }

    @Override
    public List<Event> findByEventType(String eventType) {
        return springRepo.findByEventType_TypeName(eventType);
    }

    @Override
    public boolean existsByNameAndProductionCompany(String eventName, String productionCompanyId) {
        return springRepo.existsByNameAndProductionCompanyName(eventName, productionCompanyId);
    }

    @Override
    public List<Event> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return springRepo.findByDateRange(startDate, endDate);
    }

    @Override
    public List<Event> findRaffleEnabledEvents() {
        return springRepo.findRaffleEnabledEvents();
    }

    @Override
    public boolean updateEventInventory(String eventId, Event updatedEvent) {
        if (!springRepo.existsById(eventId)) throw optimisticLockFailure(eventId, updatedEvent.getVersion());
        store(updatedEvent);   
        return true;
    }

    @Override
    public int countByProductionCompanyId(String productionCompanyId) {
        return springRepo.countByProductionCompanyName(productionCompanyId);
    }

       @Override
    public List<Event> searchEvents(String productionCompanyName, List<String> keywords,
                                    String category, LocalDateTime startDate, LocalDateTime endDate,
                                    String location, Double minPrice, Double maxPrice) {
        // Filtering is pushed to the database via a Criteria Specification instead of loading
        // every row and filtering in Java (the anti-pattern an ORM exists to avoid).
        return springRepo.findAll(buildSearchSpecification(
                productionCompanyName, keywords, category, startDate, endDate, location, minPrice, maxPrice));
    }

    private static Specification<Event> buildSearchSpecification(String productionCompanyName, List<String> keywords,
            String category, LocalDateTime startDate, LocalDateTime endDate,
            String location, Double minPrice, Double maxPrice) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));

            if (productionCompanyName != null && !productionCompanyName.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("productionCompanyName")),
                        "%" + productionCompanyName.toLowerCase() + "%"));
            }

            if (keywords != null) {
                for (String keyword : keywords) {
                    if (keyword == null || keyword.isEmpty())
                        continue;
                    String pattern = "%" + keyword.toLowerCase() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("name")), pattern),
                            cb.like(cb.lower(root.get("artist")), pattern)));
                }
            }

            if (category != null && !category.isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("eventType").get("typeName")), category.toLowerCase()));
            }

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("endDate"), endDate));
            }

            if (location != null && !location.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("location")), "%" + location.toLowerCase() + "%"));
            }

            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxPrice"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("minPrice"), maxPrice));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    public List<Event> findByManagerId(String managerId) {
        return springRepo.findByManagerId(managerId);
    }
}