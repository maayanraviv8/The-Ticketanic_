package domain.Aggregates.event;

/**
 * Lifecycle of a {@link Raffle}.
 * <p>
 * {@code OPEN} — registration is open (until the registration deadline / sale-open moment).
 * {@code DRAWN} — winners have been drawn; only winners holding a valid code may reserve.
 */
public enum RaffleStatus {
    OPEN,
    DRAWN
}
