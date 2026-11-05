package domain.policies.purchase.composite;

import application.dto.PurchasePolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.exceptions.PolicyEvaluationException;
import domain.policies.purchase.IPurchaseRule;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * OrPurchasePolicy - AT LEAST ONE nested rule must be satisfied
 * Returns true if any rule evaluates to true
 */
@Entity
@DiscriminatorValue("OR_POLICY")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(toBuilder = true)
public class OrPurchasePolicy extends IPurchaseRule {


    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_policy_id")
    @Builder.Default
    private List<IPurchaseRule> rules = new ArrayList<>();

    @Override
    public boolean evaluate(CustomerDetails context) throws Exception {

        // If there are no rules defined, there's nothing to block the purchase
        if (rules == null || rules.isEmpty()) {
            return true;
        }

        int failureCount = 0;
        StringBuilder combinedErrors = new StringBuilder();

        for (IPurchaseRule rule : rules) {
            try {
                // Try evaluating the rule. If it succeeds (no exception thrown),
                // the OR condition is met, and we can safely authorize the purchase.
                rule.evaluate(context);
                return true; // At least one rule passed, so the OR condition is satisfied
            } catch (PolicyEvaluationException e) {
                // Track failures and accumulate their specific error messages
                failureCount++;
                combinedErrors.append("\n - ").append(e.getMessage());
            }
        }

        // If the number of failures equals the total number of rules, the entire policy fails
        if (failureCount == rules.size()) {
            throw new PolicyEvaluationException("None of the required conditions were met:" + combinedErrors.toString());
        }
        return true;

    }

    @Override
    public String getDescription() {
        return "AT LEAST ONE of: " + rules.stream()
                .map(IPurchaseRule::getDescription)
                .reduce((a, b) -> a + " OR " + b)
                .orElse("(no rules)");
    }
    @Override
    public PurchasePolicyDTO toDTO() {
        return PurchasePolicyDTO.builder()
                .type("OR")
                .children(this.rules.stream().map(IPurchaseRule::toDTO).toList())
                .build();
    }
}

