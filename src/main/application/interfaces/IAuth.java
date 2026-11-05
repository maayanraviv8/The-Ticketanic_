package application.interfaces;

public interface IAuth {
    String generateToken(String email);
    boolean validateToken(String token);
    String extractEmailFromToken(String token);
}
