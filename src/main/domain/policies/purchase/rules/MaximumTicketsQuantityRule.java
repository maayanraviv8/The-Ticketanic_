package domain.policies.purchase.rules;

import application.dto.PurchasePolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.exceptions.PolicyEvaluationException;
import domain.policies.purchase.IPurchaseRule;
import jakarta.persistence.*;
import lombok.*;


/**
 * MaximumTicketsPerUserRule - Limits the total tickets a user can own
 */
@Entity
@DiscriminatorValue("MAX_TICKETS")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(toBuilder = true)
public class MaximumTicketsQuantityRule extends IPurchaseRule {

    @Column(name = "max_quantity")
    private int maxQuantity;

    @Override
    public boolean evaluate(CustomerDetails context) throws Exception {
        if (context.getTicketQuantity() > maxQuantity) {
            throw new PolicyEvaluationException("Requested quantity (" + context.getTicketQuantity() + ") exceeds the maximum allowed limit of " + maxQuantity);
        }
        return true;
    }

    @Override
    public String getDescription() {
        return String.format("Maximum %d tickets per user", maxQuantity);
    }

    public PurchasePolicyDTO toDTO() {
        return PurchasePolicyDTO.builder()
                .type("MAX_TICKETS")
                .maxTickets(this.maxQuantity)
                .build();
    }
}

