package application.dto;

import domain.Aggregates.show.VenueMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueMapDTO {
    public String venueMapId;
    public String showId;
    public String mapName;
    public int totalCapacity;
    public int zoneCount;
    public List<ZoneDTO> zones;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public static VenueMapDTO fromEntity(VenueMap venueMap) {
        if (venueMap == null) return null;
        List<ZoneDTO> zoneDTOs = venueMap.getZones() == null ? List.of() :
                venueMap.getZones().stream().map(ZoneDTO::fromEntity).toList();
        return new VenueMapDTO(
                venueMap.getVenueMapId(),
                venueMap.getShowId(),
                venueMap.getMapName(),
                venueMap.getTotalCapacity(),
                venueMap.getZoneCount(),
                zoneDTOs,
                venueMap.getCreatedAt(),
                venueMap.getUpdatedAt()
        );
    }
}
