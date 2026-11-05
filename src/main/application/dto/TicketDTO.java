package application.dto;

import domain.Aggregates.ticket.Ticket;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketDTO {
    private String id;
    private String showId;
    private String zoneId;
    private String seatId; // null for standing tickets
    private double price;

    public static TicketDTO fromEntity(Ticket ticket) {
        if (ticket == null) return null;
        return new TicketDTO(
                ticket.getId(),
                ticket.getShowId(),
                ticket.getZoneId(),
                ticket.getSeatId(),
                ticket.getPrice());
    }
}
