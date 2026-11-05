package domain.exceptions;

public class EventPolicyViolationException extends RuntimeException {
    public EventPolicyViolationException(String message) { super(message); }
}
