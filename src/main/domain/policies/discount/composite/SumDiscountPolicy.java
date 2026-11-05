package domain.policies.discount.composite;

import application.dto.DiscountPolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.policies.discount.IDiscountRule;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * SumDiscountPolicy - Returns the SUM of all active discounts
 * Stacking discounts: multiple discounts can combine (with a cap at 100%)
 */
@Entity
@DiscriminatorValue("SUM_POLICY")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SumDiscountPolicy extends IDiscountRule {


    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_policy_id")
    private List<IDiscountRule> rules = new ArrayList<>();

    @Override
    public double evaluate(CustomerDetails context) {
        double totalDiscount = rules.stream()
                .mapToDouble(rule -> rule.evaluate(context))
                .sum();

        // Cap at 100% (cannot discount more than the full price)
        return Math.min(totalDiscount, 100.0);
    }

    @Override
    public String getDescription() {
        return "SUM of: " + rules.stream()
                .map(IDiscountRule::getDescription)
                .reduce((a, b) -> a + " + " + b)
                .orElse("(no rules)");
    }

    @Override
    public DiscountPolicyDTO toDTO() {
        return DiscountPolicyDTO.builder()
                .type("SUM")
                .children(this.rules.stream()
                        .map(IDiscountRule::toDTO) //
                        .toList())
                .build();
    }
}

