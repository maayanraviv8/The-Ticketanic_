package domain.exceptions;

/**
 * Thrown when an optimistic locking conflict occurs, 
 * meaning another thread modified the entity concurrently.
 */
public class OptimisticLockingFailureException extends RuntimeException {
    public OptimisticLockingFailureException(String message) {
        super(message);
    }
}
