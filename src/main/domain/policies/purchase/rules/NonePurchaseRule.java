package domain.policies.purchase.rules;

import application.dto.PurchasePolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.policies.purchase.IPurchaseRule;


import jakarta.persistence.*;
import lombok.*;

@Entity
@DiscriminatorValue("NONE")
@Getter
@Setter
public class NonePurchaseRule extends IPurchaseRule {

    @Override
    public boolean evaluate(CustomerDetails context) {
        return true;
    }

    @Override
    public String getDescription() {
        return "No restrictions";
    }

    @Override
    public PurchasePolicyDTO toDTO() {
        return PurchasePolicyDTO.builder().type("NONE").build();
    }
}
