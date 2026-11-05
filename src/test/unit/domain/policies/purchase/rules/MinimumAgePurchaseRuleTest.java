package unit.domain.policies.purchase.rules;

import application.dto.PurchasePolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.policies.purchase.rules.MinimumAgePurchaseRule;
import domain.exceptions.PolicyEvaluationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MinimumAgePurchaseRuleTest {

    @Test
    void givenMinimumAgeRule_whenCustomerIsOlder_thenDoesNotThrow() {
        // Arrange
        MinimumAgePurchaseRule rule = new MinimumAgePurchaseRule(18);
        CustomerDetails context = CustomerDetails.builder()
                .age(25)
                .build();

        // Act & Assert
        assertDoesNotThrow(() -> rule.evaluate(context));
    }

    @Test
    void givenMinimumAgeRule_whenCustomerAgeEqualsLimit_thenDoesNotThrow() {
        // Arrange
        MinimumAgePurchaseRule rule = new MinimumAgePurchaseRule(18);
        CustomerDetails context = CustomerDetails.builder()
                .age(18)
                .build();

        // Act & Assert
        assertDoesNotThrow(() -> rule.evaluate(context));
    }

    @Test
    void givenMinimumAgeRule_whenCustomerIsTooYoung_thenThrowsPolicyEvaluationException() {
        // Arrange
        MinimumAgePurchaseRule rule = new MinimumAgePurchaseRule(16);
        CustomerDetails context = CustomerDetails.builder()
                .age(14) // Too young
                .build();

        // Act & Assert
        PolicyEvaluationException exception = assertThrows(PolicyEvaluationException.class, () ->
                rule.evaluate(context)
        );

        // Verify the message contains the actual age and required age
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("14"));
        assertTrue(exception.getMessage().contains("16"));
    }

    @Test
    void givenMinimumAgeRule_thenDescriptionIsCorrect() {
        // Arrange
        MinimumAgePurchaseRule rule = new MinimumAgePurchaseRule(16);

        // Act & Assert
        assertEquals("Minimum age requirement: 16 years", rule.getDescription());
    }

    @Test
    void givenMinimumAgeRule_whenToDTO_thenDTOMatchedCorrectly() {
        // Arrange
        MinimumAgePurchaseRule rule = new MinimumAgePurchaseRule(16);

        // Act
        PurchasePolicyDTO dto = rule.toDTO();

        // Assert
        assertNotNull(dto);
        assertNotNull(dto.getType());
        assertTrue(dto.getChildren() == null || dto.getChildren().isEmpty());
    }
}
