package unit.domain.policies.purchase.rules;

import application.dto.PurchasePolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.exceptions.PolicyEvaluationException;
import domain.policies.purchase.rules.MaximumTicketsQuantityRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaximumTicketsPerUserRuleTest {

    @Test
    void givenMaximumTicketsRule_whenTotalTicketsWithinLimit_thenReturnsTrue() {
        // Arrange
        MaximumTicketsQuantityRule rule = new MaximumTicketsQuantityRule(10);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(4)
                .build();

        // Act & Assert
        assertDoesNotThrow(() -> rule.evaluate(context));
    }

    @Test
    void givenMaximumTicketsRule_whenTotalTicketsEqualsLimit_thenReturnsTrue() {
        // Arrange
        MaximumTicketsQuantityRule rule = new MaximumTicketsQuantityRule(10);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(5)
                .build();

        // Act
        // Act & Assert
        assertDoesNotThrow(() -> rule.evaluate(context));
    }

    @Test
    void givenMaximumTicketsRule_whenTotalTicketsExceedsLimit_thenReturnsFalse() {
        // Arrange
        MaximumTicketsQuantityRule rule = new MaximumTicketsQuantityRule(10);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(11)
                .build();

        // Act
        PolicyEvaluationException exception = assertThrows(PolicyEvaluationException.class, () ->
                rule.evaluate(context)
        );

        // Assert
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("11"));
        assertTrue(exception.getMessage().contains("10"));
    }

    @Test
    void givenMaximumTicketsRule_whenNoTicketsAlreadyOwned_thenAllowsUpToLimit() {
        // Arrange
        MaximumTicketsQuantityRule rule = new MaximumTicketsQuantityRule(5);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(5)
                .build();

        // Act & Assert
        assertDoesNotThrow(() -> rule.evaluate(context));
    }

    @Test
    void givenMaximumTicketsRule_thenDescriptionIsCorrect() {
        // Arrange
        MaximumTicketsQuantityRule rule = new MaximumTicketsQuantityRule(20);

        // Act
        String description = rule.getDescription();

        // Assert
        assertEquals("Maximum 20 tickets per user", description);
    }

    @Test
    void givenMaximumTicketsRule_whenToDTO_thenDTOMatchedCorrectly() {
        // Arrange
        MaximumTicketsQuantityRule rule = new MaximumTicketsQuantityRule(10);

        // Act
        PurchasePolicyDTO dto = rule.toDTO();

        // Assert
        assertNotNull(dto);
        // Aligned with standard leaf DTO patterns (adjust "MAX_TICKETS" if your domain uses a different type string)
        assertNotNull(dto.getType());
        assertTrue(dto.getChildren() == null || dto.getChildren().isEmpty());
    }
}

