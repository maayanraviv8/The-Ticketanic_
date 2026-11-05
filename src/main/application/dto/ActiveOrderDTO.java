package application.dto;

import domain.Aggregates.activeorder.ActiveOrder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class ActiveOrderDTO {
    public String id;
    public String eventId;
    public String showId;
    public List<String> ticketIds;
    public double subtotal;
    public LocalDateTime expirationTime;
    public double discountAmount;
    public double totalPrice;

    public static ActiveOrderDTO fromEntity(ActiveOrder order) {
        if (order == null) return null;
        ActiveOrderDTO dto = new ActiveOrderDTO();
        dto.id = order.getId();
        dto.eventId = order.getEventId();
        dto.showId = order.getShowId();
        dto.ticketIds = order.getTicketIds() != null ? new ArrayList<>(order.getTicketIds()) : new ArrayList<>();
        dto.subtotal = order.getSubtotal();
        dto.expirationTime = order.getExpirationTime();
        dto.discountAmount = order.getDiscountAmount();
        dto.totalPrice = order.getTotalPrice();
        return dto;
    }
}
