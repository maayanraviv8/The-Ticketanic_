package domain.exceptions;

/**
 * Thrown for production company errors, such as the company not being found or being in an invalid state for the requested operation.
 */
public class ProductionCompanyException extends RuntimeException {
    public ProductionCompanyException(String message) {
        super(message);
    }
}