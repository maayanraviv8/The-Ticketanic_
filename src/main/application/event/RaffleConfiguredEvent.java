package application.event;

import java.time.LocalDateTime;

/**
 * Published by {@code EventService.configureRaffle} after a raffle is (re)configured, so the
 * scheduling layer can submit a one-shot draw job at the registration deadline without
 * {@code EventService} depending on the scheduler (which would be a circular dependency).
 *
 * @param eventId             the event whose raffle was configured
 * @param registrationDeadline the moment the sale opens and the draw becomes due
 */
public record RaffleConfiguredEvent(String eventId, LocalDateTime registrationDeadline) {
}
