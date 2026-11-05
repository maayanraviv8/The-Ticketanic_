package domain.exceptions;

public class DuplicateShowException extends RuntimeException {
    public DuplicateShowException(String eventId, String date) { super("A show for event '" + eventId + "' already exists on " + date + "."); }
}
