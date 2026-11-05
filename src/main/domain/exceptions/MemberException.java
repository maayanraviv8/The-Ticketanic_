package domain.exceptions;

/**
 * Thrown for member-related errors, such as the member not being found
 * or being in an invalid state for the requested operation.
 */
public class MemberException extends RuntimeException {
    public MemberException(String message) {
        super(message);
    }
}