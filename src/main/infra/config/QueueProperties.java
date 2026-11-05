package infra.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ticketanic.queue")
public record QueueProperties(
        @Min(1) int maxConcurrentUsers,
        @Min(1) int browsingSessionMinutes
) {
    public QueueProperties {
        if (maxConcurrentUsers == 0) maxConcurrentUsers = 50;
        if (browsingSessionMinutes == 0) browsingSessionMinutes = 10;
    }
}
