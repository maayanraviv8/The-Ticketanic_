package infra.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ticketanic.security.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @Min(1) int expiryMinutes
) {
    public JwtProperties {
        if (expiryMinutes == 0) expiryMinutes = 60;
    }
}
