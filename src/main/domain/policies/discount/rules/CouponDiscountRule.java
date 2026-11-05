package domain.policies.discount.rules;

import application.dto.DiscountPolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.policies.discount.IDiscountRule;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * CouponDiscountRule - Applies discount if valid coupon code is provided
 */
@Entity
@DiscriminatorValue("COUPON")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponDiscountRule extends IDiscountRule {
    private String couponCode;
    private double discountPercentage;
    private LocalDateTime expiryDate;


    @Override
    public double evaluate(CustomerDetails context) {
        // Check if coupon code matches
        if (context.getCouponCode() == null || !context.getCouponCode().equals(couponCode)) {
            return 0.0;
        }

        // Check if coupon is expired
        if (LocalDateTime.now().isAfter(expiryDate)) {
            return 0.0;
        }

        return discountPercentage;
    }

    @Override
    public String getDescription() {
        return String.format("Coupon '%s': %.1f%% discount (expires %s)",
                couponCode, discountPercentage, expiryDate);
    }

    @Override
    public DiscountPolicyDTO toDTO() {
        return DiscountPolicyDTO.builder()
                .type("COUPON")
                .percentage(this.discountPercentage)
                .couponCode(this.couponCode)
                .validUntil(this.expiryDate)
                .build();
    }
}

