package unit.external.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import infra.security.BcryptPasswordEncoder;

public class BcryptPasswordEncoderTest {

    private BcryptPasswordEncoder encoder;

    @BeforeEach
    public void setUp() {
        encoder = new BcryptPasswordEncoder();
    }

    @Test
    public void givenRawPassword_whenEncoded_thenMatches() {
        String password = "mySecretPassword";
        String encoded = encoder.encode(password);

        assertNotEquals(password, encoded);
        assertTrue(encoder.matches(password, encoded));
    }

    @Test
    public void givenSamePassword_whenEncodedTwice_thenHashesAreDifferent() {
        String password = "mySecretPassword";
        String encoded1 = encoder.encode(password);
        String encoded2 = encoder.encode(password);

        assertNotEquals(encoded1, encoded2, "Hashes should be different due to random salting");
        assertTrue(encoder.matches(password, encoded1));
        assertTrue(encoder.matches(password, encoded2));
    }

    @Test
    public void givenEncodedPassword_whenMatchingWithIncorrectPassword_thenResultIsFalse() {
        String password = "mySecretPassword";
        String encoded = encoder.encode(password);

        assertFalse(encoder.matches("wrongPassword", encoded));
    }

    @Test
    public void givenInvalidHashFormat_whenCheckingMatch_thenResultIsFalse() {
        assertFalse(encoder.matches("password", "invalidHash"));
    }
}
