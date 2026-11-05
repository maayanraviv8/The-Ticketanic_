package infra.security;

import application.interfaces.IPasswordEncoder;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Component;

/**
 * An implementation of IPasswordEncoder using the BCrypt hashing algorithm.
 * Note: This implementation requires the 'jbcrypt' library (e.g., org.mindrot:jbcrypt).
 */
@Component
public class BcryptPasswordEncoder implements IPasswordEncoder {

    @Override
    public String encode(String rawPassword) {
        // Generates a salt and hashes the password
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        // Verifies the raw password against the hashed one
        try {
            return BCrypt.checkpw(rawPassword, encodedPassword);
        } catch (Exception e) {
            // Handle cases where encodedPassword might not be a valid BCrypt hash
            return false;
        }
    }
}
