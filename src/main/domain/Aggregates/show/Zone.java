package domain.Aggregates.show;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Zone - Represents a seating or standing zone in a show
 */
@Entity
@Table(name = "zones")
@Slf4j
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Zone {
    @Id
    @Column(name = "zone_id")
    private String zoneId;

    @Column(name = "show_id", nullable = false)
    private String showId;

    @Column(name = "zone_name")
    private String zoneName;

    private boolean standing;

    @Column(name = "base_price")
    private double basePrice;

    private int rows;

    @Column(name = "seats_per_row")
    private int seatsPerRow;

    @Column(name = "standing_capacity")
    private int standingCapacity;

    @Column(name = "standing_available")
    private int standingAvailable; // Added for lock tracking

    @Column(name = "total_capacity")
    private int totalCapacity;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    @Builder.Default
    private List<Seat> seats = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void addSeat(Seat seat) {
        if (!standing) {
            this.seats.add(seat);
        }
    }

    /**
     * Number of places in this zone that are already sold or reserved (locked).
     * For a standing zone this is the capacity that is no longer available;
     * for a seated zone it is the count of taken seats.
     */
    public int getOccupiedCapacity() {
        if (standing) {
            return standingCapacity - standingAvailable;
        }
        int taken = 0;
        for (Seat seat : seats) {
            if (seat.isTaken()) {
                taken++;
            }
        }
        return taken;
    }

    public boolean lockSeats(List<String> seatIds) {
        if (standing)
            return false;

        log.info("Attempting to lock {} seats in Zone ID: {}", seatIds.size(), zoneId);

        List<Seat> targetSeats = new ArrayList<>();
        for (String seatId : seatIds) {
            Seat found = null;
            for (Seat seat : seats) {
                if (seat.getId().equals(seatId)) {
                    found = seat;
                    break;
                }
            }
            if (found == null || found.isTaken()) {
                log.warn("Failed to lock seat {}; already taken or not found in Zone ID: {}", seatId, zoneId);
                return false;
            }
            targetSeats.add(found);
        }

        for (Seat seat : targetSeats) {
            seat.setTaken(true);
        }
        return true;
    }

    public void releaseSeats(List<String> seatIds) {
        if (standing)
            return;

        for (String seatId : seatIds) {
            for (Seat seat : seats) {
                if (seat.getId().equals(seatId)) {
                    seat.setTaken(false);
                    break;
                }
            }
        }
    }

    public boolean lockStandingTickets(int quantity) {
        if (!standing)
            return false;

        log.info("Attempting to lock {} standing tickets in Zone ID: {}", quantity, zoneId);

        if (standingAvailable >= quantity) {
            standingAvailable -= quantity;
            return true;
        }

        log.info("Failed to lock standing tickets in Zone ID: {}; requested {}, but only {} available.", zoneId,
                quantity, standingAvailable);
        return false;
    }

    public void releaseStandingTickets(int quantity) {
        if (!standing)
            return;

        standingAvailable += quantity;
        if (standingAvailable > standingCapacity) {
            standingAvailable = standingCapacity;
        }
    }
}
