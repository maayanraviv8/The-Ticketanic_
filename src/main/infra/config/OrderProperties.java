package infra.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code ticketanic.order.*} configuration for {@code OrderService}.
 *
 * @param salesCounterMaxRetries how many times to retry the Event sales-counter update
 *                               on an optimistic-locking conflict during checkout
 */
@Validated
@ConfigurationProperties(prefix = "ticketanic.order")
public record OrderProperties(
        @Min(1) int salesCounterMaxRetries
) {
    public OrderProperties {
        if (salesCounterMaxRetries == 0) salesCounterMaxRetries = 5;
    }
}
