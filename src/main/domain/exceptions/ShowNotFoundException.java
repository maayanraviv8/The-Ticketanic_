package domain.exceptions;

public class ShowNotFoundException extends RuntimeException {
    public ShowNotFoundException(String showId) { super("Show not found: " + showId); }
}
