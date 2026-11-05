package unit.domain.policies.discount.conditions;

import application.dto.DiscountPolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.policies.discount.conditions.MaximumQuantityCondition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaximumQuantityConditionTest {

    @Test
    void givenMaximumQuantityCondition_whenQuantityWithinLimit_thenReturnTrue() {
        // Arrange
        MaximumQuantityCondition condition = new MaximumQuantityCondition(5);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(3)
                .build();

        // Act
        boolean result = condition.isMet(context);

        // Assert
        assertTrue(result);
    }

    @Test
    void givenMaximumQuantityCondition_whenQuantityEqualsLimit_thenReturnTrue() {
        // Arrange
        MaximumQuantityCondition condition = new MaximumQuantityCondition(5);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(5)
                .build();

        // Act
        boolean result = condition.isMet(context);

        // Assert
        assertTrue(result);
    }

    @Test
    void givenMaximumQuantityCondition_whenQuantityExceedsLimit_thenReturnFalse() {
        // Arrange
        MaximumQuantityCondition condition = new MaximumQuantityCondition(5);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(6)
                .build();

        // Act
        boolean result = condition.isMet(context);

        // Assert
        assertFalse(result);
    }

    @Test
    void givenMaximumQuantityCondition_thenDescriptionIsCorrect() {
        // Arrange
        MaximumQuantityCondition condition = new MaximumQuantityCondition(10);

        // Act
        String description = condition.getDescription();

        // Assert
        assertEquals("buying <= 10 tickets", description);
    }
    @Test
    void givenMaximumQuantityCondition_whenEnrichDTO_thenBuilderEnrichedCorrectly() {
        // Arrange
        MaximumQuantityCondition condition = new MaximumQuantityCondition(5);
        DiscountPolicyDTO.DiscountPolicyDTOBuilder builder = DiscountPolicyDTO.builder();

        // Act
        condition.enrichDTO(builder);
        DiscountPolicyDTO dto = builder.build();

        // Assert
        assertNotNull(dto);
        assertEquals(5, dto.getMaxTickets());
    }
}

