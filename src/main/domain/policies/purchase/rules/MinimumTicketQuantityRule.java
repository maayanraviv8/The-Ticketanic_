package domain.policies.purchase.rules;

import application.dto.PurchasePolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.exceptions.PolicyEvaluationException;
import domain.policies.purchase.IPurchaseRule;
import jakarta.persistence.*;
import lombok.*;

/**
 * MinimumTicketQuantityRule - Ensures a minimum number of tickets are purchased
 */
@Entity
@DiscriminatorValue("MIN_TICKETS")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(toBuilder = true)
public class MinimumTicketQuantityRule extends IPurchaseRule {

    @Column(name = "minimum_quantity")
    private int minimumQuantity;

    @Override
    public boolean evaluate(CustomerDetails context) throws Exception{

        if (context.getTicketQuantity() < minimumQuantity) {
            throw new PolicyEvaluationException("Requested quantity (" + context.getTicketQuantity() + ") is below the minimum required limit of " + minimumQuantity);
        }
        return true;
    }

    @Override
    public String getDescription() {
        return String.format("Minimum purchase quantity: %d tickets", minimumQuantity);
    }

    @Override
    public PurchasePolicyDTO toDTO() {
        return PurchasePolicyDTO.builder()
                .type("MIN_TICKETS")
                .minTickets(this.minimumQuantity)
                .build();
    }
}

