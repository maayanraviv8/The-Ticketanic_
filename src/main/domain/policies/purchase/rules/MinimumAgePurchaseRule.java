package domain.policies.purchase.rules;

import application.dto.PurchasePolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.exceptions.PolicyEvaluationException;
import domain.policies.purchase.IPurchaseRule;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.*;

/**
 * MinimumAgePurchaseRule - Ensures buyer meets minimum age requirement
 */
@Entity
@DiscriminatorValue("MIN_AGE")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(toBuilder = true)
public class MinimumAgePurchaseRule extends IPurchaseRule {

    @Column(name = "minimum_age")
    private int minimumAge;

    @Override
    public boolean evaluate(CustomerDetails context) throws Exception{
        if (context.getAge() < minimumAge) {
            throw new PolicyEvaluationException("Customer age (" + context.getAge() + ") is below the minimum required age of " + minimumAge);
        }
        return true;
    }

    @Override
    public String getDescription() {
        return String.format("Minimum age requirement: %d years", minimumAge);
    }

    @Override
    public PurchasePolicyDTO toDTO() {
        return PurchasePolicyDTO.builder()
                .type("MIN_AGE")
                .minAge(this.minimumAge)
                .build();
    }
}

