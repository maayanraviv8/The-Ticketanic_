package application.dto;

import domain.Aggregates.member.Suspension;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class SuspensionDTO {
    public String memberEmail;
    public LocalDateTime suspensionDate;
    public LocalDateTime suspensionEndDate;
    public Duration duration;

    public SuspensionDTO(String memberEmail, LocalDateTime suspensionDate, LocalDateTime suspensionEndDate, Duration duration) {
        this.memberEmail = memberEmail;
        this.suspensionDate = suspensionDate;
        this.suspensionEndDate = suspensionEndDate;
        this.duration = duration;
    }

    public static SuspensionDTO from(String memberEmail, Suspension suspension) {
        LocalDateTime end = suspension.getEndDate();
        Duration duration = end != null
            ? Duration.between(suspension.getSuspensionDate(), end)
            : Duration.between(suspension.getSuspensionDate(), LocalDateTime.now());
        return new SuspensionDTO(memberEmail, suspension.getSuspensionDate(), end, duration);
    }
}