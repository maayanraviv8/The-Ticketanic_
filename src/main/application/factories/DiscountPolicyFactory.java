package application.factories;

import application.dto.DiscountPolicyDTO;
import domain.policies.discount.IDiscountRule;
import domain.policies.discount.composite.MaxDiscountPolicy;
import domain.policies.discount.composite.SumDiscountPolicy;
import domain.policies.discount.conditions.IDiscountCondition;
import domain.policies.discount.conditions.MaximumQuantityCondition;
import domain.policies.discount.conditions.MinimumQuantityCondition;
import domain.policies.discount.conditions.TimeBasedCondition;
import domain.policies.discount.rules.ConditionalDiscountRule;
import domain.policies.discount.rules.CouponDiscountRule;
import domain.policies.discount.rules.SimplePercentageDiscountRule;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DiscountPolicyFactory {

    public IDiscountRule createFromDTO(DiscountPolicyDTO dto) {
        if (dto == null || dto.getType() == null) {
            return new SimplePercentageDiscountRule(0.0);
        }
        switch (dto.getType().toUpperCase()) {

            case "MAX":
                return new MaxDiscountPolicy(parseChildren(dto.getChildren()));

            case "SUM":
                return new SumDiscountPolicy(parseChildren(dto.getChildren()));

            case "SIMPLE":
                return new SimplePercentageDiscountRule(dto.getPercentage());

            case "CONDITIONAL_TICKETS":
                IDiscountCondition condition = parseCondition(dto);
                return new ConditionalDiscountRule(dto.getPercentage(), condition);

            case "COUPON":
                return new CouponDiscountRule(dto.getCouponCode(), dto.getPercentage(), dto.getValidUntil());

            default:
                throw new IllegalArgumentException("Unknown discount policy type: " + dto.getType());
        }
    }


    private List<IDiscountRule> parseChildren(List<DiscountPolicyDTO> childrenDTOs) {
        if (childrenDTOs == null || childrenDTOs.isEmpty()) {
            return Collections.emptyList();
        }
        return childrenDTOs.stream()
                .map(this::createFromDTO)
                .collect(Collectors.toList());
    }

    private IDiscountCondition parseCondition(DiscountPolicyDTO dto) {
        if (dto.getMinTickets() != null) {
            return new MinimumQuantityCondition(dto.getMinTickets());
        }

        if (dto.getMaxTickets() != null) {
            return new MaximumQuantityCondition(dto.getMaxTickets());
        }

        if (dto.getValidFrom() != null || dto.getValidUntil() != null) {
            return new TimeBasedCondition(dto.getValidFrom(), dto.getValidUntil());
        }

        throw new IllegalArgumentException("Conditional policy has no valid condition fields initialized.");
    }
}
