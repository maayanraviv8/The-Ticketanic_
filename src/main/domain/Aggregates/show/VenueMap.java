package domain.Aggregates.show;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * VenueMap - Represents the hall layout and mapping for a show
 */
@Entity
@Table(name = "venue_maps")
@Slf4j
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class VenueMap {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "venue_map_id")
    private String venueMapId;

    @Column(name = "show_id", nullable = false)
    private String showId;

    @Column(name = "map_name")
    private String mapName;

    @Column(name = "total_capacity")
    private int totalCapacity;

    @Column(name = "zone_count")
    private int zoneCount;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_map_id")
    @Builder.Default
    private List<Zone> zones = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void addZone(Zone zone) {
        zones.add(zone);
    }

    /**
     * Total number of places across all zones that are already sold or reserved.
     * Used to guard against shrinking or removing a layout below what has already
     * been committed to buyers.
     */
    public int getOccupiedCapacity() {
        int occupied = 0;
        for (Zone zone : zones) {
            occupied += zone.getOccupiedCapacity();
        }
        return occupied;
    }

    // --- Locking and Routing Logic ---
    public Zone getZoneById(String zoneId) {
        for (Zone zone : zones) {
            if (zone.getZoneId().equals(zoneId)) {
                return zone;
            }
        }
        return null;
    }

    public boolean lockSeatsInZone(String zoneId, List<String> seatIds) {
        Zone zone = getZoneById(zoneId);
        if (zone != null) {
            return zone.lockSeats(seatIds);
        }
        log.warn("Routing failure: Zone ID {} not found in VenueMap for seat locking.", zoneId);
        return false;
    }

    public void releaseSeatsInZone(String zoneId, List<String> seatIds) {
        Zone zone = getZoneById(zoneId);
        if (zone != null) {
            zone.releaseSeats(seatIds);
        }
    }

    public boolean lockStandingTicketsInZone(String zoneId, int quantity) {
        Zone zone = getZoneById(zoneId);
        if (zone != null) {
            return zone.lockStandingTickets(quantity);
        }
        log.warn("Routing failure: Zone ID {} not found in VenueMap for standing ticket locking.", zoneId);
        return false;
    }

    public void releaseStandingTicketsInZone(String zoneId, int quantity) {
        Zone zone = getZoneById(zoneId);
        if (zone != null) {
            zone.releaseStandingTickets(quantity);
        }
    }

    public Seat getSeatById(String zoneId, String seatId) {
        Zone zone = getZoneById(zoneId);
        if (zone != null && !zone.isStanding()) {
            for (Seat seat : zone.getSeats()) {
                if (seat.getId().equals(seatId)) {
                    return seat;
                }
            }
        }
        return null;
    }
    // ----------------------------------
}
