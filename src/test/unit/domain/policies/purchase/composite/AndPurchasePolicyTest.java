package unit.domain.policies.purchase.composite;

import application.dto.PurchasePolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.exceptions.PolicyEvaluationException;
import domain.policies.purchase.IPurchaseRule;
import domain.policies.purchase.composite.AndPurchasePolicy;
import domain.policies.purchase.rules.MaximumTicketsQuantityRule;
import domain.policies.purchase.rules.MinimumTicketQuantityRule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AndPurchasePolicyTest {

    @Test
    void givenAndPurchasePolicy_whenAllRulesSatisfied_thenReturnsTrue() {
        // Arrange
        List<IPurchaseRule> rules = Arrays.asList(
                new MinimumTicketQuantityRule(2),
                new MaximumTicketsQuantityRule(10)
        );
        AndPurchasePolicy policy = new AndPurchasePolicy(rules);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(5)
                .build();

        // Act & Assert
        assertDoesNotThrow(() -> policy.evaluate(context));

    }

    @Test
    void givenAndPurchasePolicy_whenOneRuleNotSatisfied_thenReturnsFalse() {
        // Arrange
        List<IPurchaseRule> rules = Arrays.asList(
                new MinimumTicketQuantityRule(5),
                new MaximumTicketsQuantityRule(10)
        );
        AndPurchasePolicy policy = new AndPurchasePolicy(rules);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(3)  // Below minimum of 5
                .build();

        // Act
        PolicyEvaluationException exception = assertThrows(PolicyEvaluationException.class, () ->
                policy.evaluate(context)
        );

        // Assert
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("minimum"));
    }


    @Test
    void givenAndPurchasePolicy_withSingleRule_thenUsesOnlyThatRule() {
        // Arrange
        List<IPurchaseRule> rules = Collections.singletonList(
                new MinimumTicketQuantityRule(2)
        );
        AndPurchasePolicy policy = new AndPurchasePolicy(rules);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(3)
                .build();

        // Act & Assert
        assertDoesNotThrow(() -> policy.evaluate(context));
    }

    @Test
    void givenAndPurchasePolicy_withEmptyRules_thenReturnsTrue() {
        // Arrange
        List<IPurchaseRule> rules = Collections.emptyList();
        AndPurchasePolicy policy = new AndPurchasePolicy(rules);
        CustomerDetails context = CustomerDetails.builder()
                .build();

        // Act & Assert
        assertDoesNotThrow(() -> policy.evaluate(context));
    }

    @Test
    void givenAndPurchasePolicy_thenDescriptionIsCorrect() {
        // Arrange
        List<IPurchaseRule> rules = Arrays.asList(
                new MinimumTicketQuantityRule(2),
                new MaximumTicketsQuantityRule(10)
        );
        AndPurchasePolicy policy = new AndPurchasePolicy(rules);

        // Act
        String description = policy.getDescription();

        // Assert
        assertTrue(description.contains("ALL"));
        assertTrue(description.contains("AND"));
    }

    @Test
    void givenAndPurchasePolicy_withEmptyRules_thenDescriptionShowsNoRules() {
        // Arrange
        List<IPurchaseRule> rules = Collections.emptyList();
        AndPurchasePolicy policy = new AndPurchasePolicy(rules);

        // Act
        String description = policy.getDescription();

        // Assert
        assertEquals("ALL of: (no rules)", description);
    }

    @Test
    void givenAndPurchasePolicy_whenToDTO_thenDTOMatchedCorrectly() {
        // Arrange
        List<IPurchaseRule> rules = Arrays.asList(
                new MinimumTicketQuantityRule(2)
        );
        AndPurchasePolicy policy = new AndPurchasePolicy(rules);

        // Act
        PurchasePolicyDTO dto = policy.toDTO();

        // Assert
        assertNotNull(dto);
        assertEquals("AND", dto.getType());
        assertEquals(1, dto.getChildren().size());
    }
}

