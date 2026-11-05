package unit.domain.policies.discount.composite;

import application.dto.DiscountPolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.policies.discount.IDiscountRule;
import domain.policies.discount.composite.SumDiscountPolicy;
import domain.policies.discount.rules.SimplePercentageDiscountRule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SumDiscountPolicyTest {

    @Test
    void givenSumDiscountPolicy_withMultipleRules_thenReturnsSummedDiscount() {
        // Arrange
        List<IDiscountRule> rules = Arrays.asList(
                new SimplePercentageDiscountRule(10.0),
                new SimplePercentageDiscountRule(20.0),
                new SimplePercentageDiscountRule(15.0)
        );
        SumDiscountPolicy policy = new SumDiscountPolicy(rules);
        CustomerDetails context = CustomerDetails.builder()
                .build();

        // Act
        double discount = policy.evaluate(context);

        // Assert
        assertEquals(45.0, discount);
    }

    @Test
    void givenSumDiscountPolicy_whenSumExceedsHundred_thenCapAtHundred() {
        // Arrange
        List<IDiscountRule> rules = Arrays.asList(
                new SimplePercentageDiscountRule(60.0),
                new SimplePercentageDiscountRule(50.0)
        );
        SumDiscountPolicy policy = new SumDiscountPolicy(rules);
        CustomerDetails context = CustomerDetails.builder()
                .build();

        // Act
        double discount = policy.evaluate(context);

        // Assert
        assertEquals(100.0, discount);
    }

    @Test
    void givenSumDiscountPolicy_withSingleRule_thenReturnsThatDiscount() {
        // Arrange
        List<IDiscountRule> rules = Collections.singletonList(
                new SimplePercentageDiscountRule(35.0)
        );
        SumDiscountPolicy policy = new SumDiscountPolicy(rules);
        CustomerDetails context = CustomerDetails.builder()
                .build();

        // Act
        double discount = policy.evaluate(context);

        // Assert
        assertEquals(35.0, discount);
    }

    @Test
    void givenSumDiscountPolicy_withEmptyRules_thenReturnsZero() {
        // Arrange
        List<IDiscountRule> rules = Collections.emptyList();
        SumDiscountPolicy policy = new SumDiscountPolicy(rules);
        CustomerDetails context = CustomerDetails.builder()
                .build();

        // Act
        double discount = policy.evaluate(context);

        // Assert
        assertEquals(0.0, discount);
    }

    @Test
    void givenSumDiscountPolicy_thenDescriptionIsCorrect() {
        // Arrange
        List<IDiscountRule> rules = Arrays.asList(
                new SimplePercentageDiscountRule(10.0),
                new SimplePercentageDiscountRule(20.0)
        );
        SumDiscountPolicy policy = new SumDiscountPolicy(rules);

        // Act
        String description = policy.getDescription();

        // Assert
        assertTrue(description.contains("SUM"));
        assertTrue(description.contains("+"));
    }

    @Test
    void givenSumDiscountPolicy_allowsStacking_whereBothDiscountsApply() {
        // Arrange
        List<IDiscountRule> rules = Arrays.asList(
                new SimplePercentageDiscountRule(25.0),
                new SimplePercentageDiscountRule(25.0)
        );
        SumDiscountPolicy policy = new SumDiscountPolicy(rules);
        CustomerDetails context = CustomerDetails.builder()
                .build();

        // Act
        double discount = policy.evaluate(context);

        // Assert
        assertEquals(50.0, discount, "SumPolicy should allow stacking of multiple discounts");
    }

    @Test
    void givenSumPolicy_whenToDTO_thenDTOMatchedWithChildren() {
        // Arrange
        IDiscountRule rule1 = new SimplePercentageDiscountRule(10.0);
        SumDiscountPolicy policy = new SumDiscountPolicy(Arrays.asList(rule1));

        // Act
        DiscountPolicyDTO dto = policy.toDTO();

        // Assert
        assertNotNull(dto);
        assertEquals("SUM", dto.getType());
        assertNotNull(dto.getChildren());
        assertEquals(1, dto.getChildren().size());
        assertEquals("SIMPLE", dto.getChildren().get(0).getType());
    }
}

