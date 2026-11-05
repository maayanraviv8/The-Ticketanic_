package repository.memoryRepo;

import domain.Aggregates.show.Show;
import domain.Aggregates.show.VenueMap;
import domain.Irepository.IShowRepository;
import domain.exceptions.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Profile({"dev", "test"})
@Repository
public class ShowMemoryRepo implements IShowRepository {

    private static final Logger logger = LoggerFactory.getLogger(ShowMemoryRepo.class);

    private final ConcurrentHashMap<String, Show> shows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, VenueMap> venueMaps = new ConcurrentHashMap<>();

    @Override
    public void store(Show entity) {
        if (entity.getShowId() == null || entity.getShowId().isBlank()) {
            entity.setShowId(UUID.randomUUID().toString());
            entity.setVersion(0L);
            shows.put(entity.getShowId(), entity);
            logger.info("Stored new show id={} for event id={}", entity.getShowId(), entity.getEventId());
            return;
        }
        updateShowZones(entity.getShowId(), entity);
    }

    @Override
    public void delete(String id) {
        Show removed = shows.remove(id);
        if (removed == null) {
            logger.warn("Attempted to delete non-existent show id={}", id);
            throw new IllegalArgumentException("Show not found: " + id);
        }
        venueMaps.remove(id);
        logger.info("Deleted show id={}", id);
    }

    @Override
    public Show findById(String id) {
        Show stored = shows.get(id);
        return stored == null ? null : deepCopyShow(stored);
    }

    @Override
    public List<Show> getAll() {
        return new ArrayList<>(shows.values());
    }

    @Override
    public List<Show> findByEventId(String eventId) {
        return shows.values().stream()
                .filter(s -> eventId.equals(s.getEventId()))
                .toList();
    }

    @Override
    public List<Show> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return shows.values().stream()
                .filter(s -> !s.getDate().isBefore(startDate) && !s.getDate().isAfter(endDate))
                .toList();
    }

    @Override
    public boolean existsByEventIdAndDate(String eventId, LocalDateTime showDate) {
        return shows.values().stream()
                .anyMatch(s -> eventId.equals(s.getEventId()) && s.getDate().equals(showDate));
    }

    @Override
    public List<Show> findByEventIdOrderByDate(String eventId) {
        return shows.values().stream()
                .filter(s -> eventId.equals(s.getEventId()))
                .sorted(Comparator.comparing(Show::getDate))
                .toList();
    }

    @Override
    public int getTotalInventoryByShowId(String showId) {
        Show show = shows.get(showId);
        return show == null ? 0 : show.getTotalCapacity();
    }

    @Override
    public boolean updateShowZones(String showId, Show updatedShow) {
        final long expectedVersion = updatedShow.getVersion();

        Show[] result = new Show[1];
        shows.compute(showId, (id, current) -> {
            if (current == null) {
                logger.warn("updateShowZones: show id={} not found", id);
                result[0] = null;
                return null;
            }
            if (current.getVersion() != expectedVersion) {
                logger.warn("Optimistic locking conflict on show id={}: expected={}, actual={}",
                        id, expectedVersion, current.getVersion());
                result[0] = null;
                return current;
            }
            updatedShow.setVersion(expectedVersion + 1);
            Show deepCopied = deepCopyShow(updatedShow);
            result[0] = deepCopied;
            return deepCopied;
        });

        if (result[0] == null) {
            throw new OptimisticLockException(
                    "Concurrent modification detected for show id=" + showId
                            + " (expected version=" + expectedVersion + ")");
        }

        if (result[0].getVenueMap() != null) {
            venueMaps.put(showId, result[0].getVenueMap());
        }

        logger.info("Updated show id={} to version={}", showId, result[0].getVersion());
        return true;
    }


    @Override
    public int countByEventId(String eventId) {
        return (int) shows.values().stream()
                .filter(s -> eventId.equals(s.getEventId()))
                .count();
    }

    // ── Venue map operations ──────────────────────────────────────────────────

    @Override
    public boolean saveVenueMapLayout(String showId, Object venueMapData) {
        VenueMap map = (VenueMap) venueMapData;
        venueMaps.put(showId, map);
        logger.info("Saved venue map for show id={}", showId);
        return true;
    }

    @Override
    public VenueMap getVenueMapByShowId(String showId) {
        VenueMap stored = venueMaps.get(showId);
        return stored == null ? null : deepCopyVenueMap(stored);
    }

    @Override
    public boolean validateMapCapacityMatch(String showId, Object venueMapData) {
        return true;
    }

    @Override
    public boolean updateVenueMapLayout(String showId, Object venueMapData) {
        VenueMap incoming = (VenueMap) venueMapData;
        if (!venueMaps.containsKey(showId)) {
            logger.warn("updateVenueMapLayout: no venue map found for show id={}", showId);
            return false;
        }
        venueMaps.put(showId, incoming);
        logger.info("Updated venue map for show id={}", showId);
        return true;
    }

    @Override
    public boolean deleteVenueMap(String showId) {
        return venueMaps.remove(showId) != null;
    }

    @Override
    public boolean venueMapExists(String showId) {
        return venueMaps.containsKey(showId);
    }

    @Override
    public int getTotalMapCapacity(String showId) {
        return getTotalInventoryByShowId(showId);
    }

    private Show deepCopyShow(Show show) {
        Show copy = show.toBuilder().build();
        if (show.getVenueMap() != null) {
            copy.setVenueMap(deepCopyVenueMap(show.getVenueMap()));
        }
        return copy;
    }

    private VenueMap deepCopyVenueMap(VenueMap map) {
        VenueMap copy = map.toBuilder().build();
        if (map.getZones() != null) {
            List<domain.Aggregates.show.Zone> zonesCopy = new ArrayList<>();
            for (domain.Aggregates.show.Zone zone : map.getZones()) {
                zonesCopy.add(deepCopyZone(zone));
            }
            copy.setZones(zonesCopy);
        }
        return copy;
    }

    private domain.Aggregates.show.Zone deepCopyZone(domain.Aggregates.show.Zone zone) {
        domain.Aggregates.show.Zone copy = zone.toBuilder().build();
        if (zone.getSeats() != null) {
            List<domain.Aggregates.show.Seat> seatsCopy = new ArrayList<>();
            for (domain.Aggregates.show.Seat seat : zone.getSeats()) {
                seatsCopy.add(seat.toBuilder().build());
            }
            copy.setSeats(seatsCopy);
        }
        return copy;
    }
}
