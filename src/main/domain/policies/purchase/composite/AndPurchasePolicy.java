package domain.policies.purchase.composite;

import application.dto.PurchasePolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.policies.purchase.IPurchaseRule;
import jakarta.persistence.*;
import lombok.*;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * AndPurchasePolicy - ALL nested rules must be satisfied
 * Returns true only if every rule evaluates to true
 */
@Entity
@DiscriminatorValue("AND_POLICY")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(toBuilder = true)
public class AndPurchasePolicy extends IPurchaseRule {


    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_policy_id")
    @Builder.Default
    private List<IPurchaseRule> rules = new ArrayList<>();

    @Override
    public boolean evaluate(CustomerDetails context) throws Exception {

        for (IPurchaseRule rule : rules) {
            rule.evaluate(context);
        }
        return true;
    }

    @Override
    public String getDescription() {
        return "ALL of: " + rules.stream()
                .map(IPurchaseRule::getDescription)
                .reduce((a, b) -> a + " AND " + b)
                .orElse("(no rules)");
    }

    @Override
    public PurchasePolicyDTO toDTO() {
        return PurchasePolicyDTO.builder()
                .type("AND")
                .children(this.rules.stream().map(IPurchaseRule::toDTO).toList())
                .build();
    }
}

