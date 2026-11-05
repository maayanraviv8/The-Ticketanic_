package application.service;

import application.dto.ShowDTO;
import application.dto.VenueMapDTO;
import application.dto.ZoneDTO;
import application.interfaces.IAuth;
import domain.Aggregates.show.Seat;
import domain.Aggregates.show.Show;
import domain.Aggregates.show.VenueMap;
import domain.Aggregates.show.Zone;
import domain.Irepository.IShowRepository;
import domain.exceptions.DuplicateShowException;
import domain.exceptions.InvalidShowException;
import domain.exceptions.ShowNotFoundException;
import domain.exceptions.UnauthorizedException;
import domain.exceptions.VenueMapAlreadyExistsException;
import domain.exceptions.VenueMapNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ShowService {

    private static final Logger logger = LoggerFactory.getLogger(ShowService.class);

    private final IShowRepository showRepository;
    private final IAuth authService;
    private final EventService eventService;

    public ShowService(IShowRepository showRepository, IAuth authService, EventService eventService) {
        this.showRepository = showRepository;
        this.authService = authService;
        this.eventService = eventService;
    }

    private String authenticate(String token) {
        if (token == null || !authService.validateToken(token))
            throw new UnauthorizedException("Token is invalid or has expired");
        String userId = authService.extractEmailFromToken(token);
        if (userId == null) throw new UnauthorizedException("Could not identify user from token");
        return userId;
    }

    public ShowDTO createShow(String eventId, ShowDTO showDTO, String token) {
        String userId = authenticate(token);
        logger.info("User '{}' creating show for event id={} on date={}", userId, eventId, showDTO.date);
        validateShowDetails(showDTO);

        if (showRepository.existsByEventIdAndDate(eventId, showDTO.date))
            throw new DuplicateShowException(eventId, showDTO.date.toString());

        int totalCapacity = showDTO.zones == null ? 0 : showDTO.zones.stream()
                .mapToInt(z -> z.standing ? z.standingCapacity : z.rows * z.seatsPerRow).sum();

        Show show = Show.builder()
                .eventId(eventId)
                .date(showDTO.date)
                .totalCapacity(totalCapacity)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        showRepository.store(show);

        if (showDTO.zones != null && !showDTO.zones.isEmpty()) {
            VenueMapDTO tempMapDTO = new VenueMapDTO();
            tempMapDTO.zones = showDTO.zones;
            VenueMap venueMap = VenueMap.builder()
                    .showId(show.getShowId())
                    .totalCapacity(totalCapacity)
                    .zoneCount(showDTO.zones.size())
                    .zones(buildZones(show.getShowId(), tempMapDTO))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            showRepository.saveVenueMapLayout(show.getShowId(), venueMap);
            logger.info("Successfully saved venue map for show id={}", show.getShowId());
        }

        logger.info("Successfully created show id={} for event id={}", show.getShowId(), eventId);
        return ShowDTO.fromEntity(show);
    }

    public ShowDTO editShow(String showId, ShowDTO showDTO, String token) {
        String userId = authenticate(token);
        logger.info("User '{}' editing show id={}", userId, showId);

        Show existing = showRepository.findById(showId);
        if (existing == null) throw new ShowNotFoundException(showId);

        validateShowDetails(showDTO);

        existing.setDate(showDTO.date);
        // Capacity is owned by the venue map (synced by the hall-layout methods); only
        // recompute it here when the caller actually supplies zones, so a date-only edit
        // can't clobber the map-derived capacity.
        if (showDTO.zones != null) {
            int totalCapacity = showDTO.zones.stream()
                    .mapToInt(z -> z.standing ? z.standingCapacity : z.rows * z.seatsPerRow).sum();
            existing.setTotalCapacity(totalCapacity);
        }
        existing.setUpdatedAt(LocalDateTime.now());

        showRepository.updateShowZones(showId, existing);
        logger.info("Successfully updated show id={}", showId);
        return ShowDTO.fromEntity(existing);
    }

    public void removeShow(String showId, String token) {
        String userId = authenticate(token);
        logger.info("User '{}' removing show id={}", userId, showId);

        Show existing = showRepository.findById(showId);
        if (existing == null) throw new ShowNotFoundException(showId);

        showRepository.delete(showId);
        logger.info("Successfully removed show id={}", showId);
    }

    public List<ShowDTO> getShowsByEvent(String eventId) {
        logger.info("Fetching shows for event id={}", eventId);
        List<ShowDTO> shows = showRepository.findByEventIdOrderByDate(eventId)
                .stream().map(ShowDTO::fromEntity).toList();
        if (shows.isEmpty()) {
            logger.info("No shows found for event id={}", eventId);
        } else {
            logger.info("Found {} show(s) for event id={}", shows.size(), eventId);
        }
        return shows;
    }

    public ShowDTO getShowById(String showId) {
        logger.info("Fetching show map for show id={}", showId);
        Show show = showRepository.findById(showId);
        if (show == null) {
            logger.warn("Show not found: id={}", showId);
            throw new ShowNotFoundException(showId);
        }
        logger.info("Successfully retrieved show map for show id={}", showId);
        return ShowDTO.fromEntity(show);
    }

    // ── UC15 - Define Hall Layout in Event Map ────────────────────────────────

    public VenueMapDTO defineHallLayout(String showId, VenueMapDTO venueMapDTO, String token) {
        String userId = authenticate(token);
        logger.info("User '{}' defining hall layout for show id={}", userId, showId);

        Show show = showRepository.findById(showId);
        if (show == null) throw new ShowNotFoundException(showId);
        eventService.authorizeHallLayoutManagement(showId, userId);

        if (showRepository.venueMapExists(showId))
            throw new VenueMapAlreadyExistsException(showId);

        VenueMap venueMap = VenueMap.builder()
                .showId(showId)
                .mapName(venueMapDTO.mapName)
                .totalCapacity(venueMapDTO.totalCapacity)
                .zoneCount(venueMapDTO.zoneCount)
                .zones(buildZones(showId, venueMapDTO))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        showRepository.saveVenueMapLayout(showId, venueMap);
        syncShowCapacityToMap(showId, venueMap);
        logger.info("Successfully saved venue map for show id={}", showId);
        return VenueMapDTO.fromEntity(venueMap);
    }

    ///////// UC4 - View Show Map /////////

    public VenueMapDTO getHallLayout(String showId) {
        logger.info("Fetching hall layout for show id={}", showId);
        VenueMap venueMap = showRepository.getVenueMapByShowId(showId);
        if (venueMap == null) throw new VenueMapNotFoundException(showId);
        return VenueMapDTO.fromEntity(venueMap);
    }

    public VenueMapDTO updateHallLayout(String showId, VenueMapDTO venueMapDTO, String token) {
        String userId = authenticate(token);
        logger.info("User '{}' updating hall layout for show id={}", userId, showId);

        Show show = showRepository.findById(showId);
        if (show == null) throw new ShowNotFoundException(showId);
        eventService.authorizeHallLayoutManagement(showId, userId);

        if (!showRepository.venueMapExists(showId))
            throw new VenueMapNotFoundException(showId);

        // Reject edits once seats have been committed to buyers: rebuilding the layout
        // would reset seat availability and could re-sell already purchased/reserved seats.
        VenueMap existingMap = showRepository.getVenueMapByShowId(showId);
        int occupiedSeats = existingMap == null ? 0 : existingMap.getOccupiedCapacity();
        if (occupiedSeats > 0)
            throw new InvalidShowException("Cannot modify the hall layout: " + occupiedSeats +
                    " seat(s) have already been sold or reserved for this show.");

        VenueMap venueMap = VenueMap.builder()
                .showId(showId)
                .mapName(venueMapDTO.mapName)
                .totalCapacity(venueMapDTO.totalCapacity)
                .zoneCount(venueMapDTO.zoneCount)
                .zones(buildZones(showId, venueMapDTO))
                .createdAt(venueMapDTO.createdAt)
                .updatedAt(LocalDateTime.now())
                .build();

        showRepository.updateVenueMapLayout(showId, venueMap);
        syncShowCapacityToMap(showId, venueMap);
        logger.info("Successfully updated venue map for show id={}", showId);
        return VenueMapDTO.fromEntity(venueMap);
    }

    public void removeHallLayout(String showId, String token) {
        String userId = authenticate(token);
        logger.info("User '{}' removing hall layout for show id={}", userId, showId);

        eventService.authorizeHallLayoutManagement(showId, userId);

        if (!showRepository.venueMapExists(showId))
            throw new VenueMapNotFoundException(showId);

        // Removing a layout that still has sold/reserved seats would orphan those
        // purchases, so block it while any seat is committed.
        VenueMap existingMap = showRepository.getVenueMapByShowId(showId);
        int occupiedSeats = existingMap == null ? 0 : existingMap.getOccupiedCapacity();
        if (occupiedSeats > 0)
            throw new InvalidShowException("Cannot remove the hall layout: " + occupiedSeats +
                    " seat(s) have already been sold or reserved for this show.");

        showRepository.deleteVenueMap(showId);
        syncShowCapacityToMap(showId, null);
        logger.info("Successfully removed venue map for show id={}", showId);
    }

    /**
     * Keeps the show's denormalized {@code totalCapacity} in sync with its venue map: the sum
     * of the map's zone capacities, or 0 when the map is removed. The venue map is the single
     * source of truth for capacity, so every path that changes the map (any view, or a direct
     * API call) lands here and the stored value can no longer drift. Reloads the show so the
     * write is independent of the map save above and still respects optimistic locking.
     */
    private void syncShowCapacityToMap(String showId, VenueMap map) {
        Show show = showRepository.findById(showId);
        if (show == null) return;
        int capacity = map == null ? 0
                : map.getZones().stream().mapToInt(Zone::getTotalCapacity).sum();
        show.setTotalCapacity(capacity);
        show.setUpdatedAt(LocalDateTime.now());
        showRepository.updateShowZones(showId, show);
    }

    // ── Validation & helpers ──────────────────────────────────────────────────

    private List<Zone> buildZones(String showId, VenueMapDTO venueMapDTO) {
        if (venueMapDTO.zones == null) return new ArrayList<>();
        List<Zone> zones = new ArrayList<>();
        // Mint globally-unique zone/seat ids per build so a re-built (edited) layout never
        // reuses the previous layout's primary keys, which would collide with the old rows
        // under JPA cascade/orphan-removal and corrupt the persisted seats.
        String token = UUID.randomUUID().toString();
        int zoneIdx = 1;
        for (ZoneDTO zoneDTO : venueMapDTO.zones) {
            zones.add(buildZone(zoneDTO, "z-" + token + "-" + (zoneIdx++), showId));
        }
        return zones;
    }

    private Zone buildZone(ZoneDTO zoneDTO, String zoneId, String showId) {
        int capacity = zoneDTO.standing
                ? zoneDTO.standingCapacity
                : zoneDTO.rows * zoneDTO.seatsPerRow;

        List<Seat> seats = new ArrayList<>();
        if (!zoneDTO.standing) {
            for (int row = 1; row <= zoneDTO.rows; row++) {
                for (int seatNum = 1; seatNum <= zoneDTO.seatsPerRow; seatNum++) {
                    seats.add(new Seat(zoneId + "-r" + row + "s" + seatNum, row, seatNum, false, zoneDTO.basePrice));
                }
            }
        }

        return Zone.builder()
                .zoneId(zoneId)
                .showId(showId)
                .zoneName(zoneDTO.zoneName)
                .standing(zoneDTO.standing)
                .basePrice(zoneDTO.basePrice)
                .rows(zoneDTO.rows)
                .seatsPerRow(zoneDTO.seatsPerRow)
                .standingCapacity(zoneDTO.standingCapacity)
                .standingAvailable(zoneDTO.standing ? zoneDTO.standingCapacity : 0)
                .totalCapacity(capacity)
                .seats(seats)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void validateShowDetails(ShowDTO showDTO) {
        if (showDTO.date == null)
            throw new InvalidShowException("Show date must not be null.");
        if (showDTO.date.isBefore(LocalDateTime.now()))
            throw new InvalidShowException("Show date must be in the future.");
        if (showDTO.zones != null) {
            for (ZoneDTO zone : showDTO.zones) {
                if (zone.standing && zone.standingCapacity < 0)
                    throw new InvalidShowException("Standing capacity must not be negative for zone '" + zone.zoneName + "'.");
                if (!zone.standing && (zone.rows < 0 || zone.seatsPerRow < 0))
                    throw new InvalidShowException("Rows and seats per row must not be negative for zone '" + zone.zoneName + "'.");
                if (zone.basePrice < 0)
                    throw new InvalidShowException("Base price must not be negative for zone '" + zone.zoneName + "'.");
            }
        }
    }
}
