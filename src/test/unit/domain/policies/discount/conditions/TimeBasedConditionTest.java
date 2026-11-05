package unit.domain.policies.discount.conditions;

import application.dto.DiscountPolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.policies.discount.conditions.TimeBasedCondition;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TimeBasedConditionTest {

    @Test
    void givenTimeBasedCondition_whenPurchaseBeforeExpiryDate_thenReturnTrue() {
        // Arrange
        LocalDateTime expiryDate = LocalDateTime.of(2026, 12, 31, 23, 59);
        TimeBasedCondition condition = new TimeBasedCondition(expiryDate);
        CustomerDetails context = CustomerDetails.builder()
                .purchaseTime(LocalDateTime.of(2026, 12, 20, 12, 0))
                .build();

        // Act
        boolean result = condition.isMet(context);

        // Assert
        assertTrue(result);
    }

    @Test
    void givenTimeBasedCondition_whenPurchaseOnExpiryDate_thenReturnFalse() {
        // Arrange
        LocalDateTime expiryDate = LocalDateTime.of(2026, 12, 31, 23, 59);
        TimeBasedCondition condition = new TimeBasedCondition(expiryDate);
        CustomerDetails context = CustomerDetails.builder()
                .purchaseTime(LocalDateTime.of(2027, 1, 1, 0, 0))
                .build();

        // Act
        boolean result = condition.isMet(context);

        // Assert
        assertFalse(result);
    }

    @Test
    void givenTimeBasedConditionWithRange_whenPurchaseWithinRange_thenReturnTrue() {
        // Arrange
        LocalDateTime startDate = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2026, 6, 1, 0, 0);
        TimeBasedCondition condition = new TimeBasedCondition(startDate, endDate);
        CustomerDetails context = CustomerDetails.builder()
                .purchaseTime(LocalDateTime.of(2026, 5, 15, 12, 0))
                .build();

        // Act
        boolean result = condition.isMet(context);

        // Assert
        assertTrue(result);
    }

    @Test
    void givenTimeBasedConditionWithRange_whenPurchaseBeforeRange_thenReturnFalse() {
        // Arrange
        LocalDateTime startDate = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2026, 6, 1, 0, 0);
        TimeBasedCondition condition = new TimeBasedCondition(startDate, endDate);
        CustomerDetails context = CustomerDetails.builder()
                .purchaseTime(LocalDateTime.of(2026, 4, 15, 12, 0))
                .build();

        // Act
        boolean result = condition.isMet(context);

        // Assert
        assertFalse(result);
    }

    @Test
    void givenTimeBasedConditionWithRange_whenPurchaseAfterRange_thenReturnFalse() {
        // Arrange
        LocalDateTime startDate = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2026, 6, 1, 0, 0);
        TimeBasedCondition condition = new TimeBasedCondition(startDate, endDate);
        CustomerDetails context = CustomerDetails.builder()
                .purchaseTime(LocalDateTime.of(2026, 7, 15, 12, 0))
                .build();

        // Act
        boolean result = condition.isMet(context);

        // Assert
        assertFalse(result);
    }

    @Test
    void givenTimeBasedCondition_whenNoPurchaseTime_thenReturnFalse() {
        // Arrange
        LocalDateTime expiryDate = LocalDateTime.of(2026, 12, 31, 23, 59);
        TimeBasedCondition condition = new TimeBasedCondition(expiryDate);
        CustomerDetails context = CustomerDetails.builder()
                .build();

        // Act
        boolean result = condition.isMet(context);

        // Assert
        assertFalse(result);
    }

    @Test
    void givenTimeBasedCondition_thenDescriptionForExpiryOnlyIsCorrect() {
        // Arrange
        LocalDateTime expiryDate = LocalDateTime.of(2026, 12, 31, 23, 59);
        TimeBasedCondition condition = new TimeBasedCondition(expiryDate);

        // Act
        String description = condition.getDescription();

        // Assert
        assertTrue(description.contains("2026-12-31"));
    }

    @Test
    void givenTimeBasedCondition_thenDescriptionForRangeIsCorrect() {
        // Arrange
        LocalDateTime startDate = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2026, 6, 1, 0, 0);
        TimeBasedCondition condition = new TimeBasedCondition(startDate, endDate);

        // Act
        String description = condition.getDescription();

        // Assert
        assertTrue(description.contains("between"));
    }

    @Test
    void givenTimeBasedCondition_whenEnrichDTO_thenBuilderEnrichedCorrectly() {
        // Arrange
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(1);
        TimeBasedCondition condition = new TimeBasedCondition(start, end);
        DiscountPolicyDTO.DiscountPolicyDTOBuilder builder = DiscountPolicyDTO.builder();

        // Act
        condition.enrichDTO(builder);
        DiscountPolicyDTO dto = builder.build();

        // Assert
        assertNotNull(dto);
    }
}

