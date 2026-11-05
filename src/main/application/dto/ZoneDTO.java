package application.dto;

import domain.Aggregates.show.Zone;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ZoneDTO {
    public String zoneId;
    public String zoneName;
    public boolean standing;
    public double basePrice;
    public int rows;
    public int seatsPerRow;
    public int standingCapacity;
    public int standingAvailable;
    public int totalCapacity;
    public List<SeatDTO> seats;

    public static ZoneDTO fromEntity(Zone zone) {
        ZoneDTO dto = new ZoneDTO();
        dto.zoneId = zone.getZoneId();
        dto.zoneName = zone.getZoneName();
        dto.standing = zone.isStanding();
        dto.basePrice = zone.getBasePrice();
        dto.rows = zone.getRows();
        dto.seatsPerRow = zone.getSeatsPerRow();
        dto.standingCapacity = zone.getStandingCapacity();
        dto.standingAvailable = zone.getStandingAvailable();
        dto.totalCapacity = zone.getTotalCapacity();
        dto.seats = zone.isStanding() || zone.getSeats() == null
                ? List.of()
                : zone.getSeats().stream().map(SeatDTO::fromEntity).toList();
        return dto;
    }
}
