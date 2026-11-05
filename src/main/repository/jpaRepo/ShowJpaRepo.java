package repository.jpaRepo;

import domain.Aggregates.show.Show;
import domain.Aggregates.show.VenueMap;
import domain.Irepository.IShowRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Repository
@Profile("cloud")
public class ShowJpaRepo implements IShowRepository {
    private static final Logger logger = LoggerFactory.getLogger(ShowJpaRepo.class);

    private final ShowSpringDataRepo springRepo;

    public ShowJpaRepo(ShowSpringDataRepo springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public void store(Show entity) {
        boolean isNew = entity.getShowId() == null || entity.getShowId().isBlank();
        if (isNew) {
            entity.setShowId(java.util.UUID.randomUUID().toString());
            entity.setVersion(0L);
        }

        Show saved;
        try {
            saved = springRepo.saveAndFlush(entity);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            logger.warn("Optimistic locking conflict on show id={}: expected={}", entity.getShowId(),
                    entity.getVersion());
            throw optimisticLockFailure(entity.getShowId(), entity.getVersion());
        }

        // merge() returns a different managed-entity instance whose ID was assigned by
        // @GeneratedValue; sync it back so the caller sees the actual persisted ID.
        entity.setShowId(saved.getShowId());

        if (isNew) {
            logger.info("Stored new show id={} for event id={}", saved.getShowId(), saved.getEventId());
        } else {
            logger.info("Updated show id={} to version={}", saved.getShowId(), saved.getVersion());
        }
    }

    private domain.exceptions.OptimisticLockException optimisticLockFailure(String showId, long expectedVersion) {
        return new domain.exceptions.OptimisticLockException(
                "Concurrent modification detected for show id=" + showId
                        + " (expected version=" + expectedVersion + ")");
    }

    @Override
    public void delete(String id) {
        if (!springRepo.existsById(id)) {
            logger.warn("Attempted to delete non-existent show id={}", id);
            throw new IllegalArgumentException("Show not found: " + id);
        }

        springRepo.deleteById(id);
        logger.info("Deleted show id={}", id);
    }

    @Override
    public Show findById(String id) {
        return springRepo.findById(id).orElse(null);
    }

    @Override
    public List<Show> getAll() {
        return springRepo.findAll();
    }

    @Override
    public List<Show> findByEventId(String eventId) {
        return springRepo.findByEventId(eventId);
    }

    @Override
    public List<Show> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return springRepo.findByDateRange(startDate, endDate);
    }

    @Override
    public boolean existsByEventIdAndDate(String eventId, LocalDateTime showDate) {
        return springRepo.existsByEventIdAndDate(eventId, showDate);
    }

    @Override
    public List<Show> findByEventIdOrderByDate(String eventId) {
        return springRepo.findByEventIdOrderByDate(eventId);
    }

    @Override
    public int getTotalInventoryByShowId(String showId) {
        Show show = findById(showId);
        return show != null ? show.getTotalCapacity() : 0;
    }

    @Override
    public boolean updateShowZones(String showId, Show updatedShow) {
        if (!springRepo.existsById(showId)) {
            logger.warn("updateShowZones: show id={} not found", showId);
            throw optimisticLockFailure(showId, updatedShow.getVersion());
        }
        store(updatedShow);
        return true;
    }

    @Override
    public int countByEventId(String eventId) {
        return springRepo.countByEventId(eventId);
    }

    @Override
    public boolean saveVenueMapLayout(String showId, Object venueMapData) {
        boolean saved = applyVenueMap(showId, venueMapData);
        if (saved) {
            logger.info("Saved venue map for show id={}", showId);
        }
        return saved;
    }

    private boolean applyVenueMap(String showId, Object venueMapData) {
        Show show = findById(showId);
        if (show == null)
            return false;

        VenueMap map = (VenueMap) venueMapData;
        map.setShowId(showId);
        show.setVenueMap(map);
        store(show);
        return true;
    }

    @Override
    public VenueMap getVenueMapByShowId(String showId) {
        Show show = findById(showId);
        return show != null ? show.getVenueMap() : null;
    }

    @Override
    public boolean validateMapCapacityMatch(String showId, Object venueMapData) {
        Show show = findById(showId);
        if (show == null)
            return false;

        VenueMap map = (VenueMap) venueMapData;
        return show.getTotalCapacity() == map.getTotalCapacity();
    }

    @Override
    public boolean updateVenueMapLayout(String showId, Object venueMapData) {
        Show show = findById(showId);
        if (show == null || show.getVenueMap() == null) {
            logger.warn("updateVenueMapLayout: no venue map found for show id={}", showId);
            return false;
        }
        boolean saved = applyVenueMap(showId, venueMapData);
        if (saved) {
            logger.info("Updated venue map for show id={}", showId);
        }
        return saved;
    }

    @Override
    public boolean deleteVenueMap(String showId) {
        Show show = findById(showId);
        if (show == null || show.getVenueMap() == null)
            return false;

        show.setVenueMap(null);
        store(show);
        return true;
    }

    @Override
    public boolean venueMapExists(String showId) {
        Show show = findById(showId);
        return show != null && show.getVenueMap() != null;
    }

    @Override
    public int getTotalMapCapacity(String showId) {
        Show show = findById(showId);
        return (show != null && show.getVenueMap() != null) ? show.getVenueMap().getTotalCapacity() : 0;
    }
}