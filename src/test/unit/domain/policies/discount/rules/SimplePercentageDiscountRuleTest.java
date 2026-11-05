package unit.domain.policies.discount.rules;

import application.dto.DiscountPolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.policies.discount.rules.SimplePercentageDiscountRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimplePercentageDiscountRuleTest {

    @Test
    void givenSimplePercentageDiscount_thenAlwaysReturnsConfiguredPercentage() {
        // Arrange
        SimplePercentageDiscountRule rule = new SimplePercentageDiscountRule(15.0);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(5)
                .build();

        // Act
        double discount = rule.evaluate(context);

        // Assert
        assertEquals(15.0, discount);
    }

    @Test
    void givenSimplePercentageDiscount_withDifferentContexts_thenReturnsConsistentDiscount() {
        // Arrange
        SimplePercentageDiscountRule rule = new SimplePercentageDiscountRule(20.0);

        // Act & Assert
        CustomerDetails context1 = CustomerDetails.builder()
                .ticketQuantity(1)
                .build();
        assertEquals(20.0, rule.evaluate(context1));

        CustomerDetails context2 = CustomerDetails.builder()
                .ticketQuantity(100)
                .build();
        assertEquals(20.0, rule.evaluate(context2));
    }

    @Test
    void givenSimplePercentageDiscount_thenDescriptionIsCorrect() {
        // Arrange
        SimplePercentageDiscountRule rule = new SimplePercentageDiscountRule(25.5);

        // Act
        String description = rule.getDescription();

        // Assert
        assertTrue(description.contains("25.5"));
        assertTrue(description.contains("discount"));
    }

    @Test
    void givenSimplePercentageDiscount_withZeroPercent_thenReturnsZero() {
        // Arrange
        SimplePercentageDiscountRule rule = new SimplePercentageDiscountRule(0.0);
        CustomerDetails context = CustomerDetails.builder()
                .build();

        // Act
        double discount = rule.evaluate(context);

        // Assert
        assertEquals(0.0, discount);
    }

    @Test
    void givenSimplePercentageDiscount_withMaxPercent_thenReturns100() {
        // Arrange
        SimplePercentageDiscountRule rule = new SimplePercentageDiscountRule(100.0);
        CustomerDetails context = CustomerDetails.builder()
                .build();

        // Act
        double discount = rule.evaluate(context);

        // Assert
        assertEquals(100.0, discount);
    }

    @Test
    void givenSimpleDiscount_whenToDTO_thenDTOMatchedCorrectly() {
        // Arrange
        SimplePercentageDiscountRule rule = new SimplePercentageDiscountRule(15.0);

        // Act
        DiscountPolicyDTO dto = rule.toDTO();

        // Assert
        assertNotNull(dto);
        assertEquals("SIMPLE", dto.getType());
        assertEquals(15.0, dto.getPercentage());
    }
}

