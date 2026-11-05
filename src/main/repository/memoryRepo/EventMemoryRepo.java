package repository.memoryRepo;

import domain.Aggregates.event.Event;
import domain.Irepository.IEventRepository;
import domain.exceptions.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Profile({"dev", "test"})
@Repository
public class EventMemoryRepo implements IEventRepository {

    private static final Logger logger = LoggerFactory.getLogger(EventMemoryRepo.class);

    private final ConcurrentHashMap<String, Event> events = new ConcurrentHashMap<>();

    @Override
    public void store(Event entity) {
        if (entity.getEventId() == null || entity.getEventId().isBlank()) {
            entity.setEventId(UUID.randomUUID().toString());
            entity.setVersion(0L);
            events.put(entity.getEventId(), entity);
            logger.info("Stored new event id={} name='{}'", entity.getEventId(), entity.getName());
            return;
        }
        updateEventInventory(entity.getEventId(), entity);
    }

    @Override
    public void delete(String id) {
        Event removed = events.remove(id);
        if (removed == null) {
            logger.warn("Attempted to delete non-existent event id={}", id);
            throw new IllegalArgumentException("Event not found: " + id);
        }
        logger.info("Deleted event id={}", id);
    }

    @Override
    public Event findById(String id) {
        Event stored = events.get(id);
        return stored == null ? null : stored.toBuilder().build();
    }

    @Override
    public List<Event> getAll() {
        return new ArrayList<>(events.values());
    }

    @Override
    public List<Event> findByProductionCompanyId(String productionCompanyId) {
        return events.values().stream()
                .filter(e -> productionCompanyId.equals(e.getProductionCompanyName()))
                .toList();
    }

    @Override
    public List<Event> findByEventType(String eventType) {
        return events.values().stream()
                .filter(e -> e.getEventType() != null
                        && e.getEventType().getTypeName().equalsIgnoreCase(eventType))
                .toList();
    }

    @Override
    public boolean existsByNameAndProductionCompany(String eventName, String productionCompanyId) {
        return events.values().stream()
                .anyMatch(e -> e.getName().equalsIgnoreCase(eventName)
                        && productionCompanyId.equals(e.getProductionCompanyName()));
    }

    @Override
    public List<Event> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return events.values().stream()
                .filter(e -> !e.getStartDate().isBefore(startDate) && !e.getEndDate().isAfter(endDate))
                .toList();
    }

    @Override
    public List<Event> findRaffleEnabledEvents() {
        return events.values().stream()
                .filter(e -> e.getRaffle() != null)
                .toList();
    }

    @Override
    public boolean updateEventInventory(String eventId, Event updatedEvent) {
        final long expectedVersion = updatedEvent.getVersion();

        Event[] result = new Event[1];
        events.compute(eventId, (id, current) -> {
            if (current == null) {
                result[0] = null;
                return null;
            }
            if (current.getVersion() != expectedVersion) {
                logger.warn("Optimistic locking conflict on event id={}: expected={}, actual={}",
                        id, expectedVersion, current.getVersion());
                result[0] = null;
                return current;
            }
            updatedEvent.setVersion(expectedVersion + 1);
            result[0] = updatedEvent;
            return updatedEvent;
        });

        if (result[0] == null) {
            throw new OptimisticLockException(
                    "Concurrent modification detected for event id=" + eventId
                            + " (expected version=" + expectedVersion + ")");
        }

        logger.info("Updated event id={} to version={}", eventId, result[0].getVersion());
        return true;
    }

    @Override
    public int countByProductionCompanyId(String productionCompanyId) {
        return (int) events.values().stream()
                .filter(e -> productionCompanyId.equals(e.getProductionCompanyName()))
                .count();
    }

    @Override
    public List<Event> findByManagerId(String managerId) {
        return events.values().stream()
                .filter(e -> managerId.equals(e.getManagerId()))
                .map(e -> e.toBuilder().build())
                .toList();
    }

    @Override
    public List<Event> searchEvents(String productionCompanyName, List<String> keywords,
            String category, LocalDateTime startDate, LocalDateTime endDate,
            String location, Double minPrice, Double maxPrice) {

        return events.values().stream()
            .filter(event -> event.matchesSearchCriteria(
                productionCompanyName, keywords, category,
                startDate, endDate, location, minPrice, maxPrice))
            .map(event -> event.toBuilder().build())
            .toList();
    }
}
