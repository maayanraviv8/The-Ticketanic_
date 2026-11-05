package domain.Aggregates.show;

/**
 * Position of a seat within a zone: its row and seat number.
 *
 * <p>A query-result value object exposed by the {@link Show} aggregate root so callers can
 * read a seat's coordinates without traversing the {@code VenueMap}/{@code Zone}/{@code Seat}
 * aggregate children. Immutable; carries no behaviour.
 */
public record SeatPosition(int row, int number) { }
