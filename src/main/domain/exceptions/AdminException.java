package domain.exceptions;

/**
 * Thrown for admin-related errors, such as a non-admin user attempting an admin-only operation.
 */
public class AdminException extends RuntimeException {
    public AdminException(String message) {
        super(message);
    }
}