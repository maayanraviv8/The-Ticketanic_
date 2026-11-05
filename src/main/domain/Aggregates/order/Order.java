package domain.Aggregates.order;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;


@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {
    @Id
    @Column(name = "order_id")
    private String orderId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "show_id", nullable = false)
    private String showId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "order_tickets", joinColumns = @JoinColumn(name = "order_id"))
    @Column(name = "ticket_id")
    private List<String> ticketIds;

    private double price;

    @Column(name = "purchase_time")
    private LocalDateTime purchaseTime;

    @Embedded
    private CustomerDetails customerDetails;

    /** Secure ticket code returned by the external WSEP issue_ticket action (null if not issued externally). */
    @Column(name = "external_ticket_code")
    private String externalTicketCode;

    @Version
    private long version = 0L;

    public Order(String orderId, String eventId, String showId, List<String> ticketIds,
                 double price, LocalDateTime purchaseTime, CustomerDetails customerDetails) {
        this.orderId = orderId;
        this.eventId = eventId;
        this.showId = showId;
        this.ticketIds = ticketIds;
        this.price = price;
        this.purchaseTime = purchaseTime;
        this.customerDetails = customerDetails;
    }
}
