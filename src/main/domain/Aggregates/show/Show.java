package domain.Aggregates.show;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import domain.Aggregates.ticket.Ticket;

/**
 * Show Aggregate - Represents a specific showing of an event
 */
@Entity
@Table(name = "shows")
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Show {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "show_id")
    private String showId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "show_date")
    private LocalDateTime date;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_map_id")
    private VenueMap venueMap;

    @Column(name = "total_capacity")
    private int totalCapacity;
    @Column(name = "tickets_sold")
    @Builder.Default
    private int ticketsSold = 0;
    @Column(name = "total_revenue")
    @Builder.Default
    private double totalRevenue = 0.0;

    @Builder.Default
    private boolean active = true;
    @Version
    @Builder.Default
    private long version = 0L;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public List<Ticket> reserveTickets(String zoneId, List<String> seatIds, int standingQuantity,
            Supplier<String> ticketIdGenerator) throws Exception {
        List<Ticket> generatedTickets = new ArrayList<>();

        // Lock assigned seats via VenueMap
        if (seatIds != null && !seatIds.isEmpty()) {
            boolean locked = venueMap.lockSeatsInZone(zoneId, seatIds);
            if (!locked) {
                throw new Exception("One or more selected seats are unavailable.");
            }

            for (String seatId : seatIds) {
                Seat seat = venueMap.getSeatById(zoneId, seatId);
                Ticket t = new Ticket(ticketIdGenerator.get(), this.showId, zoneId, seatId, seat.getPrice());
                generatedTickets.add(t);
            }
        }

        // Lock standing tickets via VenueMap
        if (standingQuantity > 0) {
            boolean locked = venueMap.lockStandingTicketsInZone(zoneId, standingQuantity);
            if (!locked) {
                // Rollback seating locks if any were made previously
                if (seatIds != null && !seatIds.isEmpty()) {
                    venueMap.releaseSeatsInZone(zoneId, seatIds);
                }
                throw new Exception("Not enough standing capacity available in the requested zone.");
            }

            Zone zone = venueMap.getZoneById(zoneId);
            double zonePrice = zone != null ? zone.getBasePrice() : 0.0;
            for (int i = 0; i < standingQuantity; i++) {
                Ticket t = new Ticket(ticketIdGenerator.get(), this.showId, zoneId, null, zonePrice);
                generatedTickets.add(t);
            }
        }
        this.updatedAt = java.time.LocalDateTime.now();

        return generatedTickets;
    }

    /**
     * Returns the display name of a zone in this show, or the zoneId itself
     * if no zone with that id exists. Used so callers don't need to traverse
     * the VenueMap aggregate child.
     */
    public String getZoneName(String zoneId) {
        Zone zone = venueMap == null ? null : venueMap.getZoneById(zoneId);
        return zone != null ? zone.getZoneName() : zoneId;
    }

    /**
     * Returns the {@link SeatPosition} (row + seat number) of a seat in this show, or
     * {@code null} if the seat (or its zone) does not exist. Lets callers read seat
     * coordinates without traversing the VenueMap aggregate child.
     */
    public SeatPosition getSeatPosition(String zoneId, String seatId) {
        Seat seat = venueMap == null ? null : venueMap.getSeatById(zoneId, seatId);
        return seat == null ? null : new SeatPosition(seat.getRowNumber(), seat.getSeatNumber());
    }

    public void releaseTicket(Ticket ticket) {
        if (ticket.isStanding()) {
            venueMap.releaseStandingTicketsInZone(ticket.getZoneId(), 1);
        } else {
            venueMap.releaseSeatsInZone(ticket.getZoneId(), Collections.singletonList(ticket.getSeatId()));
        }
        this.updatedAt = java.time.LocalDateTime.now();
    }
}
