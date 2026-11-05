package domain.Aggregates.activeorder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import domain.Aggregates.ticket.Ticket;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "active_orders")
public class ActiveOrder {
    
    @Id
    @Column(name = "id")
    private String id;
    
    @Column(name = "event_id")
    private String eventId;
    
    @Column(name = "show_id")
    private String showId;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "active_order_tickets", joinColumns = @JoinColumn(name = "active_order_id"))
    @Column(name = "ticket_id")
    private List<String> ticketIds = new ArrayList<>();
    
    @Column(name = "subtotal")
    private double subtotal;
    
    @Column(name = "expiration_time")
    private LocalDateTime expirationTime;
    
    @Column(name = "discount_amount")
    private double discountAmount;

    public ActiveOrder(String id, String eventId, String showId, int lockDurationMinutes) {
        this.id = id;
        this.eventId = eventId;
        this.showId = showId;
        this.ticketIds = new ArrayList<>();
        this.subtotal = 0.0;
        this.expirationTime = LocalDateTime.now().plusMinutes(lockDurationMinutes);
        this.discountAmount = 0.0;
    }

    public void addTicket(String ticketId, double price) {
        this.ticketIds.add(ticketId);
        this.subtotal += price;
    }

    public void addTickets(List<Ticket> tickets) {
        for (Ticket t : tickets) {
            addTicket(t.getId(), t.getPrice());
        }
    }

    public boolean containsTicket(String ticketId) {
        return this.ticketIds.contains(ticketId);
    }

    public boolean removeTicket(String ticketId, double price) {
        if (this.ticketIds.remove(ticketId)) {
            this.subtotal -= price;
            return true;
        }
        return false;
    }

    public double getTotalPrice() {
        return Math.max(0, subtotal - discountAmount);
    }

    public boolean isExpired(LocalDateTime currentTime) {
        return currentTime.isAfter(expirationTime);
    }


}
