package domain.exceptions;

public class VenueMapNotFoundException extends RuntimeException {
    public VenueMapNotFoundException(String showId) { super("No venue map found for show: " + showId); }
}
