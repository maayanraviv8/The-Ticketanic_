package unit.domain.policies.discount.rules;

import application.dto.DiscountPolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.policies.discount.conditions.MinimumQuantityCondition;
import domain.policies.discount.rules.ConditionalDiscountRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConditionalDiscountRuleTest {

    @Test
    void givenConditionalDiscount_whenConditionMet_thenReturnsDiscount() {
        // Arrange
        MinimumQuantityCondition condition = new MinimumQuantityCondition(2);
        ConditionalDiscountRule rule = new ConditionalDiscountRule(10.0, condition);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(3)
                .build();

        // Act
        double discount = rule.evaluate(context);

        // Assert
        assertEquals(10.0, discount);
    }

    @Test
    void givenConditionalDiscount_whenConditionNotMet_thenReturnsZero() {
        // Arrange
        MinimumQuantityCondition condition = new MinimumQuantityCondition(2);
        ConditionalDiscountRule rule = new ConditionalDiscountRule(10.0, condition);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(1)
                .build();

        // Act
        double discount = rule.evaluate(context);

        // Assert
        assertEquals(0.0, discount);
    }

    @Test
    void givenConditionalDiscount_thenDescriptionIncludesConditionDescription() {
        // Arrange
        MinimumQuantityCondition condition = new MinimumQuantityCondition(5);
        ConditionalDiscountRule rule = new ConditionalDiscountRule(15.0, condition);

        // Act
        String description = rule.getDescription();

        // Assert
        assertTrue(description.contains("15.0"));
        assertTrue(description.contains("buying >= 5 tickets"));
    }

    @Test
    void givenConditionalDiscount_whenToDTO_thenDTOMatchedAndEnriched() {
        // Arrange
        MinimumQuantityCondition condition = new MinimumQuantityCondition(3);
        ConditionalDiscountRule rule = new ConditionalDiscountRule(12.5, condition);

        // Act
        DiscountPolicyDTO dto = rule.toDTO();

        // Assert
        assertNotNull(dto);
        assertEquals("CONDITIONAL", dto.getType());
        assertEquals(12.5, dto.getPercentage());
    }
}

