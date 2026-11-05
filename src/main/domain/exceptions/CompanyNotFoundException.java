package domain.exceptions;

public class CompanyNotFoundException extends RuntimeException {
    public CompanyNotFoundException(String message) {
        super("Production company "+message+" not found");
    }
}
