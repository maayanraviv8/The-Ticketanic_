package domain.policies.discount.composite;

import application.dto.DiscountPolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.policies.discount.IDiscountRule;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * MaxDiscountPolicy - Returns the MAXIMUM discount among all rules
 * No double-dipping: only the best discount is applied
 */
@Entity
@DiscriminatorValue("MAX_POLICY")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaxDiscountPolicy extends IDiscountRule {

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_policy_id")
    private List<IDiscountRule> rules = new ArrayList<>();;

    @Override
    public double evaluate(CustomerDetails context) {
        return rules.stream()
                .mapToDouble(rule -> rule.evaluate(context))
                .max()
                .orElse(0.0);
    }

    @Override
    public String getDescription() {
        return "MAX of: " + rules.stream()
                .map(IDiscountRule::getDescription)
                .reduce((a, b) -> a + " or " + b)
                .orElse("(no rules)");
    }

    @Override
    public DiscountPolicyDTO toDTO() {
        return DiscountPolicyDTO.builder()
                .type("MAX")
                .children(this.rules.stream()
                        .map(IDiscountRule::toDTO) //
                        .toList())
                .build();
    }
}

