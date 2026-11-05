package domain.policies.discount.conditions;

import application.dto.DiscountPolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import jakarta.persistence.*;
import lombok.*;

/**
 * MaximumQuantityCondition - Applies discount if customer buys at most a certain quantity
 * Example: "10% off if buying <= 5 tickets"
 */
@Entity
@DiscriminatorValue("MAX_QUANTITY")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(toBuilder = true)
public class MaximumQuantityCondition extends IDiscountCondition {

    @Column(name = "maximum_quantity")
    private int maximumQuantity;

    @Override
    public boolean isMet(CustomerDetails context) {
        return context.getTicketQuantity() <= maximumQuantity;
    }

    @Override
    public String getDescription() {
        return String.format("buying <= %d tickets", maximumQuantity);
    }

    @Override
    public void enrichDTO(DiscountPolicyDTO.DiscountPolicyDTOBuilder builder) {
        builder.maxTickets(this.maximumQuantity);
    }
}

