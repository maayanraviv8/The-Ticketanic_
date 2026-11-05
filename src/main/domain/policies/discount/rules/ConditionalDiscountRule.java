package domain.policies.discount.rules;

import application.dto.DiscountPolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.policies.discount.IDiscountRule;
import domain.policies.discount.conditions.IDiscountCondition;
import jakarta.persistence.*;
import lombok.*;

/**
 * ConditionalDiscountRule - Applies discount only if a condition is met
 * Examples: "10% off if buying >= 2 tickets", "15% off if under 18"
 */
@Entity
@DiscriminatorValue("CONDITIONAL")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConditionalDiscountRule extends IDiscountRule {
    private double discountPercentage;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "condition_id")
    private IDiscountCondition condition;

    @Override
    public double evaluate(CustomerDetails context) {
        return condition.isMet(context) ? discountPercentage : 0.0;
    }

    @Override
    public String getDescription() {
        return String.format("Conditional %.1f%% discount: %s",
                discountPercentage, condition.getDescription());
    }

    @Override
    public DiscountPolicyDTO toDTO() {
        DiscountPolicyDTO.DiscountPolicyDTOBuilder builder = DiscountPolicyDTO.builder()
                .type("CONDITIONAL")
                .percentage(this.discountPercentage);

        this.condition.enrichDTO(builder);

        return builder.build();
    }
}

