package application.interfaces;

public interface IPasswordEncoder {
    /**
     * Encode the raw password.
     * @param rawPassword The plain text password to encode.
     * @return The encoded (hashed) password.
     */
    String encode(String rawPassword);

    /**
     * Verify the encoded password against the raw password.
     * @param rawPassword The plain text password.
     * @param encodedPassword The hashed password from the database.
     * @return true if the passwords match, false otherwise.
     */
    boolean matches(String rawPassword, String encodedPassword);
}
