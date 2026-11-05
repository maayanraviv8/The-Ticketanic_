package unit.domain.policies.discount.conditions;

import application.dto.DiscountPolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.policies.discount.conditions.MinimumQuantityCondition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MinimumQuantityConditionTest {

    @Test
    void givenMinimumQuantityCondition_whenQuantityMeetsMinimum_thenReturnTrue() {
        // Arrange
        MinimumQuantityCondition condition = new MinimumQuantityCondition(2);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(3)
                .build();

        // Act
        boolean result = condition.isMet(context);

        // Assert
        assertTrue(result);
    }

    @Test
    void givenMinimumQuantityCondition_whenQuantityEqualsMinimum_thenReturnTrue() {
        // Arrange
        MinimumQuantityCondition condition = new MinimumQuantityCondition(2);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(2)
                .build();

        // Act
        boolean result = condition.isMet(context);

        // Assert
        assertTrue(result);
    }

    @Test
    void givenMinimumQuantityCondition_whenQuantityBelowMinimum_thenReturnFalse() {
        // Arrange
        MinimumQuantityCondition condition = new MinimumQuantityCondition(2);
        CustomerDetails context = CustomerDetails.builder()
                .ticketQuantity(1)
                .build();

        // Act
        boolean result = condition.isMet(context);

        // Assert
        assertFalse(result);
    }

    @Test
    void givenMinimumQuantityCondition_thenDescriptionIsCorrect() {
        // Arrange
        MinimumQuantityCondition condition = new MinimumQuantityCondition(5);

        // Act
        String description = condition.getDescription();

        // Assert
        assertEquals("buying >= 5 tickets", description);
    }

    @Test
    void givenMinimumQuantityCondition_whenEnrichDTO_thenBuilderEnrichedCorrectly() {
        // Arrange
        MinimumQuantityCondition condition = new MinimumQuantityCondition(3);
        DiscountPolicyDTO.DiscountPolicyDTOBuilder builder = DiscountPolicyDTO.builder();

        // Act
        condition.enrichDTO(builder);
        DiscountPolicyDTO dto = builder.build();

        // Assert
        assertNotNull(dto);
        assertEquals(3, dto.getMinTickets());
    }
}

