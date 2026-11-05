package domain.exceptions;

public class AppointmentPermissionException extends RuntimeException {
    public AppointmentPermissionException(String message) {
        super("Appointer does not have permission to appoint "+message+".");
    }
}
