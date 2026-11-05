package domain.exceptions;

public class VenueMapAlreadyExistsException extends RuntimeException {
    public VenueMapAlreadyExistsException(String showId) { super("A venue map already exists for show: " + showId); }
}
