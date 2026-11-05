package application.dto;

import domain.Aggregates.event.Event;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class EventDTO {
    public String eventId;
    public String name;
    public String eventTypeName;
    public String productionCompanyName;
    public String artist;
    public String location;
    public double minPrice;
    public double maxPrice;
    public LocalDateTime startDate;
    public LocalDateTime endDate;
    public int ticketsSold;
    public boolean active;
    public PurchasePolicyDTO purchasePolicy;
    public DiscountPolicyDTO discountPolicy;
    public  boolean requiresRaffle;
    public String raffleStatus;


    public static EventDTO fromEntity(Event event) {
        if (event == null) return null;
        EventDTO dto = new EventDTO();
        dto.eventId = event.getEventId();
        dto.name = event.getName();
        dto.eventTypeName = event.getEventType() != null ? event.getEventType().getTypeName() : null;
        dto.productionCompanyName = event.getProductionCompanyName();
        dto.artist = event.getArtist();
        dto.location = event.getLocation();
        dto.minPrice = event.getMinPrice();
        dto.maxPrice = event.getMaxPrice();
        dto.startDate = event.getStartDate();
        dto.endDate = event.getEndDate();
        dto.ticketsSold = event.getTicketsSold();
        dto.active = event.isActive();
        dto.purchasePolicy = PurchasePolicyDTO.fromEntity(event.getPurchasePolicy());
        dto.discountPolicy = DiscountPolicyDTO.fromEntity(event.getDiscountPolicy());
        dto.requiresRaffle = event.requiresRaffle();
        dto.raffleStatus = event.requiresRaffle() && event.getRaffle().getStatus() != null
                ? event.getRaffle().getStatus().name() : null;
        return dto;
    }
}
