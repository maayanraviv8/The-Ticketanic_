package infra.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ticketanic.active-order")
public record ActiveOrderProperties(
        @Min(1) int lockDurationMinutes,
        @Min(1) int maxOptimisticRetries
) {
    public ActiveOrderProperties {
        if (lockDurationMinutes == 0) lockDurationMinutes = 15;
        if (maxOptimisticRetries == 0) maxOptimisticRetries = 20;
    }
}
