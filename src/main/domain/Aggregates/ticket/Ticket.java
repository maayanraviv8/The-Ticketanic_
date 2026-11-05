package domain.Aggregates.ticket;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Ticket {
    @Id
    @Column(name = "ticket_id")
    private String id;

    @Column(name = "show_id", nullable = false)
    private String showId;

    @Column(name = "zone_id", nullable = false)
    private String zoneId;

    @Column(name = "seat_id")
    private String seatId; // Null for standing tickets

    @Column(name = "price", nullable = false)
    private double price;

    @Version
    @Builder.Default
    private long version = 0L;

    public Ticket(String id, String showId, String zoneId, String seatId, double price) {
        this.id = id;
        this.showId = showId;
        this.zoneId = zoneId;
        this.seatId = seatId;
        this.price = price;
    }

    public boolean isStanding() {
        return seatId == null;
    }
}
