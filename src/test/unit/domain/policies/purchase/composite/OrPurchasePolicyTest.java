package unit.domain.policies.purchase.composite;

import application.dto.PurchasePolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.exceptions.PolicyEvaluationException;
import domain.policies.purchase.IPurchaseRule;
import domain.policies.purchase.composite.OrPurchasePolicy;
import domain.policies.purchase.rules.MaximumTicketsQuantityRule;
import domain.policies.purchase.rules.MinimumTicketQuantityRule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrPurchasePolicyTest {

    @Test
    void givenOrPurchasePolicy_whenAllRulesSatisfied_thenReturnsTrue() {
        // Arrange
        List<IPurchaseRule> rules = Arrays.asList(
                new MinimumTicketQuantityRule(2),
                new MaximumTicketsQuantityRule(10)
        );
        OrPurchasePolicy policy = new OrPurchasePolicy(rules);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(5)
                .build();

        // Act & Assert
        assertDoesNotThrow(() -> policy.evaluate(context));
    }

    @Test
    void givenOrPurchasePolicy_whenAtLeastOneRuleSatisfied_thenReturnsTrue() {
        // Arrange
        List<IPurchaseRule> rules = Arrays.asList(
                new MinimumTicketQuantityRule(10),  // Will fail
                new MaximumTicketsQuantityRule(10)   // Will pass
        );
        OrPurchasePolicy policy = new OrPurchasePolicy(rules);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(3)
                .build();

        // Act & Assert
        assertDoesNotThrow(() -> policy.evaluate(context));
    }

    @Test
    void givenOrPurchasePolicy_whenNoRulesSatisfied_thenReturnsFalse() {
        // Arrange
        List<IPurchaseRule> rules = Arrays.asList(
                new MinimumTicketQuantityRule(10),   // Will fail (7 < 10)
                new MaximumTicketsQuantityRule(5)     // Will fail (7 > 5)
        );
        OrPurchasePolicy policy = new OrPurchasePolicy(rules);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(7)
                .build();

        // Act
        PolicyEvaluationException exception = assertThrows(PolicyEvaluationException.class, () ->
                policy.evaluate(context)
        );

        // Assert
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("None of the required conditions were met"));
        assertTrue(exception.getMessage().contains("minimum"));
        assertTrue(exception.getMessage().contains("maximum"));
    }

    @Test
    void givenOrPurchasePolicy_withSingleRule_thenUsesOnlyThatRule() {
        // Arrange
        List<IPurchaseRule> rules = Collections.singletonList(
                new MinimumTicketQuantityRule(2)
        );
        OrPurchasePolicy policy = new OrPurchasePolicy(rules);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(3)
                .build();

        // Act & Assert
        assertDoesNotThrow(() -> policy.evaluate(context));
    }

    @Test
    void givenOrPurchasePolicy_withEmptyRules_thenReturnsFalse() {
        // Arrange
        List<IPurchaseRule> rules = Collections.emptyList();
        OrPurchasePolicy policy = new OrPurchasePolicy(rules);
        CustomerDetails context = CustomerDetails.builder()
                .build();

        // Act & Assert
        assertDoesNotThrow(() -> policy.evaluate(context));
    }

    @Test
    void givenOrPurchasePolicy_thenDescriptionIsCorrect() {
        // Arrange
        List<IPurchaseRule> rules = Arrays.asList(
                new MinimumTicketQuantityRule(2),
                new MaximumTicketsQuantityRule(10)
        );
        OrPurchasePolicy policy = new OrPurchasePolicy(rules);

        // Act
        String description = policy.getDescription();

        // Assert
        assertTrue(description.contains("AT LEAST ONE"));
        assertTrue(description.contains("OR"));
    }


    @Test
    void givenOrPurchasePolicy_withEmptyRules_thenDescriptionShowsNoRules() {
        // Arrange
        List<IPurchaseRule> rules = Collections.emptyList();
        OrPurchasePolicy policy = new OrPurchasePolicy(rules);

        // Act
        String description = policy.getDescription();

        // Assert
        assertEquals("AT LEAST ONE of: (no rules)", description);
    }

    @Test
    void givenOrPurchasePolicy_whenToDTO_thenDTOMatchedCorrectly() {
        // Arrange
        List<IPurchaseRule> rules = Arrays.asList(
                new MinimumTicketQuantityRule(2)
        );
        OrPurchasePolicy policy = new OrPurchasePolicy(rules);

        // Act
        PurchasePolicyDTO dto = policy.toDTO();

        // Assert
        assertNotNull(dto);
        assertEquals("OR", dto.getType());
        assertEquals(1, dto.getChildren().size());
    }
}

