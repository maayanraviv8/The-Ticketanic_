package domain.policies.discount.conditions;

import application.dto.DiscountPolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.*;
import lombok.*;

/**
 * MinimumQuantityCondition - Applies discount if customer buys minimum quantity
 */
@Entity
@DiscriminatorValue("MIN_QUANTITY")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(toBuilder = true)
public class MinimumQuantityCondition extends IDiscountCondition {

    @Column(name = "minimum_quantity")
    private int minimumQuantity;

    @Override
    public boolean isMet(CustomerDetails context) {
        return context.getTicketQuantity() >= minimumQuantity;
    }

    @Override
    public String getDescription() {
        return String.format("buying >= %d tickets", minimumQuantity);
    }

    @Override
    public void enrichDTO(DiscountPolicyDTO.DiscountPolicyDTOBuilder builder) {
        builder.minTickets(this.minimumQuantity);
    }

}

