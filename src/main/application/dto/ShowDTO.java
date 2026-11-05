package application.dto;

import domain.Aggregates.show.Show;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShowDTO {
    public String showId;
    public String eventId;
    public LocalDateTime date;
    public int totalCapacity;
    public boolean active;
    public List<ZoneDTO> zones;

    public static ShowDTO fromEntity(Show show) {
        if (show == null) return null;
        return new ShowDTO(
                show.getShowId(),
                show.getEventId(),
                show.getDate(),
                show.getTotalCapacity(),
                show.isActive(),
                null
        );
    }
}
