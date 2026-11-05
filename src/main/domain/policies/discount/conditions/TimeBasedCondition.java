package domain.policies.discount.conditions;

import application.dto.DiscountPolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.*;

import java.time.LocalDateTime;

/**
 * TimeBasedCondition - Applies discount based on purchase time
 * Example: "15% off if purchased before 2026-12-31"
 */
@Entity
@DiscriminatorValue("TIME_BASED")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(toBuilder = true)
public class TimeBasedCondition extends IDiscountCondition {

    @Column(name = "valid_until")
    private LocalDateTime validUntil;
    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    /**
     * Constructor with only validUntil date
     */
    public TimeBasedCondition(LocalDateTime validUntil) {
        this(null, validUntil);
    }

    /**
     * Constructor with both validFrom and validUntil dates
     */
    public TimeBasedCondition(LocalDateTime validFrom, LocalDateTime validUntil) {
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    @Override
    public boolean isMet(CustomerDetails context) {
        LocalDateTime purchaseTime = context.getPurchaseTime();

        if (purchaseTime == null) {
            return false;
        }

        // Check if purchase time is within valid range
        if (validFrom != null && purchaseTime.isBefore(validFrom)) {
            return false;
        }

        if (validUntil != null && purchaseTime.isAfter(validUntil)) {
            return false;
        }

        return true;
    }

    @Override
    public String getDescription() {
        if (validFrom != null && validUntil != null) {
            return String.format("purchased between %s and %s", validFrom, validUntil);
        } else if (validUntil != null) {
            return String.format("purchased before %s", validUntil);
        } else if (validFrom != null) {
            return String.format("purchased after %s", validFrom);
        }
        return "time-based condition";
    }

    @Override
    public void enrichDTO(DiscountPolicyDTO.DiscountPolicyDTOBuilder builder) {
        builder.validFrom(this.validFrom);
        builder.validUntil(this.validUntil);
    }
}

