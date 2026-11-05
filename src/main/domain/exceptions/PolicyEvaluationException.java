package domain.exceptions;

/**
 * PolicyEvaluationException - Thrown when policy evaluation fails
 */
public class PolicyEvaluationException extends RuntimeException {
    public PolicyEvaluationException(String message) {
        super(message);
    }

    public PolicyEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}

