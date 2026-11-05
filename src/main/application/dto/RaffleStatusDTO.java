package application.dto;

import domain.Aggregates.event.Event;
import domain.Aggregates.event.Raffle;
import domain.Aggregates.event.RaffleAuthorization;

import java.time.LocalDateTime;

/**
 * The current raffle state of an event from a particular viewer's perspective (II.3.9).
 * Built with the viewer's email so it can expose "did I register / win" and the winner's code.
 */
public class RaffleStatusDTO {
    public boolean requiresRaffle;
    public String status;                 // "OPEN" / "DRAWN" / null
    public boolean registered;
    public boolean won;
    public String code;                   // null unless the viewer is a winner
    public LocalDateTime codeExpiresAt;
    public LocalDateTime registrationDeadline;

    public static RaffleStatusDTO fromEntity(Event event, String viewerEmail) {
        RaffleStatusDTO dto = new RaffleStatusDTO();
        dto.requiresRaffle = event.requiresRaffle();

        Raffle raffle = event.getRaffle();
        if (raffle == null) return dto;

        dto.status = raffle.getStatus() != null ? raffle.getStatus().name() : null;
        dto.registrationDeadline = raffle.getRegistrationDeadline();

        if (viewerEmail != null) {
            dto.registered = raffle.isParticipant(viewerEmail);
            dto.won = raffle.isWinner(viewerEmail);
            RaffleAuthorization auth = raffle.getAuthorization(viewerEmail);
            if (auth != null) {
                dto.code = auth.getCode();
                dto.codeExpiresAt = auth.getExpiresAt();
            }
        }
        return dto;
    }
}
