package application.factories;


import application.dto.PurchasePolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.policies.purchase.IPurchaseRule;
import domain.policies.purchase.composite.AndPurchasePolicy;
import domain.policies.purchase.composite.OrPurchasePolicy;
import domain.policies.purchase.rules.*;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PurchasePolicyFactory {

    public IPurchaseRule createFromDTO(PurchasePolicyDTO dto) {
        if (dto == null || dto.getType() == null) {
            // Null Object Pattern
            return new NonePurchaseRule();
        }

        switch (dto.getType().toUpperCase()) {
            case "AND":
                return new AndPurchasePolicy(parseChildren(dto.getChildren()));

            case "OR":
                return new OrPurchasePolicy(parseChildren(dto.getChildren()));

            case "MAX_TICKETS":
                int max = (dto.getMaxTickets() != null) ? dto.getMaxTickets() : Integer.MAX_VALUE;
                return new MaximumTicketsQuantityRule(max);

            case "MIN_TICKETS":
                int min = (dto.getMinTickets() != null) ? dto.getMinTickets() : 0;
                return new MinimumTicketQuantityRule(min);

            case "MIN_AGE":
                int age = (dto.getMinAge() != null) ? dto.getMinAge() : 0;
                return new MinimumAgePurchaseRule(age);

            default:
                throw new IllegalArgumentException("Unknown purchase policy type: " + dto.getType());
        }
    }

    private List<IPurchaseRule> parseChildren(List<PurchasePolicyDTO> childrenDTOs) {
        if (childrenDTOs == null) return List.of();
        return childrenDTOs.stream()
                .map(this::createFromDTO)
                .toList();
    }
}
