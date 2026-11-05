package domain.exceptions;

/**
 * Thrown when an unauthorized access attempt is detected,
 * such as an invalid or expired authentication token.
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
