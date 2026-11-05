package domain.policies.discount.rules;

import application.dto.DiscountPolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.policies.discount.IDiscountRule;
import jakarta.persistence.*;
import lombok.*;

/**
 * SimplePercentageDiscountRule - Straightforward percentage discount
 */
@Entity
@DiscriminatorValue("SIMPLE_PERCENTAGE")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimplePercentageDiscountRule extends IDiscountRule {
    private double discountPercentage;

    @Override
    public double evaluate(CustomerDetails context) {
        return discountPercentage;
    }

    @Override
    public String getDescription() {
        return String.format("Simple %.1f%% discount", discountPercentage);
    }

    @Override
    public DiscountPolicyDTO toDTO() {
        return DiscountPolicyDTO.builder()
                .type("SIMPLE")
                .percentage(this.discountPercentage)
                .build();
    }
}

