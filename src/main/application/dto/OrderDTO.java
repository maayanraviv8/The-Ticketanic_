package application.dto;

import domain.Aggregates.order.Order;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {
    private String orderId;
    private String eventId;
    private String showId;
    private List<String> ticketIds;
    private double finalPrice;
    private LocalDateTime purchaseTime;

    public static OrderDTO fromEntity(Order order) {
        if (order == null) return null;
        return new OrderDTO(
                order.getOrderId(),
                order.getEventId(),
                order.getShowId(),
                order.getTicketIds(),
                order.getPrice(),
                order.getPurchaseTime()
        );
    }
}
