package domain.exceptions;

public class MemberSuspendedException extends RuntimeException {
    public MemberSuspendedException(String message) {
        super(message);
    }
}