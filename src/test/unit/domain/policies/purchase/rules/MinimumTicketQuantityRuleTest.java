package unit.domain.policies.purchase.rules;

import application.dto.PurchasePolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.exceptions.PolicyEvaluationException;
import domain.policies.purchase.rules.MinimumTicketQuantityRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MinimumTicketQuantityRuleTest {

    @Test
    void givenMinimumQuantityRule_whenQuantityMeetsMinimum_thenReturnsTrue() {
        // Arrange
        MinimumTicketQuantityRule rule = new MinimumTicketQuantityRule(3);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(5)
                .build();

        // Act & Assert
        assertDoesNotThrow(() -> rule.evaluate(context));
    }


    @Test
    void givenMinimumQuantityRule_whenQuantityBelowMinimum_thenReturnsFalse() {
        // Arrange
        MinimumTicketQuantityRule rule = new MinimumTicketQuantityRule(3);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(2)
                .build();

        // Act
        PolicyEvaluationException exception = assertThrows(PolicyEvaluationException.class, () ->
                rule.evaluate(context)
        );

        // Assert
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("2"));
        assertTrue(exception.getMessage().contains("3"));
    }

    @Test
    void givenMinimumQuantityRule_thenDescriptionIsCorrect() {
        // Arrange
        MinimumTicketQuantityRule rule = new MinimumTicketQuantityRule(10);

        // Act
        String description = rule.getDescription();

        // Assert
        assertEquals("Minimum purchase quantity: 10 tickets", description);
    }

    @Test
    void givenMinimumQuantityRule_whenToDTO_thenDTOMatchedCorrectly() {
        // Arrange
        MinimumTicketQuantityRule rule = new MinimumTicketQuantityRule(3);

        // Act
        PurchasePolicyDTO dto = rule.toDTO();

        // Assert
        assertNotNull(dto);
        assertNotNull(dto.getType());
        assertTrue(dto.getChildren() == null || dto.getChildren().isEmpty());
    }
}

