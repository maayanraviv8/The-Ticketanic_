package unit.domain.policies.discount.composite;

import application.dto.DiscountPolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.policies.discount.IDiscountRule;
import domain.policies.discount.composite.MaxDiscountPolicy;
import domain.policies.discount.rules.SimplePercentageDiscountRule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MaxDiscountPolicyTest {

    @Test
    void givenMaxDiscountPolicy_withMultipleRules_thenReturnsMaximum() {
        // Arrange
        List<IDiscountRule> rules = Arrays.asList(
                new SimplePercentageDiscountRule(10.0),
                new SimplePercentageDiscountRule(20.0),
                new SimplePercentageDiscountRule(15.0)
        );
        MaxDiscountPolicy policy = new MaxDiscountPolicy(rules);
        CustomerDetails context = CustomerDetails.builder()
                .build();

        // Act
        double discount = policy.evaluate(context);

        // Assert
        assertEquals(20.0, discount);
    }

    @Test
    void givenMaxDiscountPolicy_withSingleRule_thenReturnsThatDiscount() {
        // Arrange
        List<IDiscountRule> rules = Collections.singletonList(
                new SimplePercentageDiscountRule(25.0)
        );
        MaxDiscountPolicy policy = new MaxDiscountPolicy(rules);
        CustomerDetails context = CustomerDetails.builder()
                .build();

        // Act
        double discount = policy.evaluate(context);

        // Assert
        assertEquals(25.0, discount);
    }

    @Test
    void givenMaxDiscountPolicy_withEmptyRules_thenReturnsZero() {
        // Arrange
        List<IDiscountRule> rules = Collections.emptyList();
        MaxDiscountPolicy policy = new MaxDiscountPolicy(rules);
        CustomerDetails context = CustomerDetails.builder()
                .build();

        // Act
        double discount = policy.evaluate(context);

        // Assert
        assertEquals(0.0, discount);
    }

    @Test
    void givenMaxDiscountPolicy_thenDescriptionIsCorrect() {
        // Arrange
        List<IDiscountRule> rules = Arrays.asList(
                new SimplePercentageDiscountRule(10.0),
                new SimplePercentageDiscountRule(20.0)
        );
        MaxDiscountPolicy policy = new MaxDiscountPolicy(rules);

        // Act
        String description = policy.getDescription();

        // Assert
        assertTrue(description.contains("MAX"));
        assertTrue(description.contains("or"));
    }

    @Test
    void givenMaxPolicy_whenToDTO_thenDTOMatchedWithChildren() {
        // Arrange
        IDiscountRule rule1 = new SimplePercentageDiscountRule(20.0);
        MaxDiscountPolicy policy = new MaxDiscountPolicy(Arrays.asList(rule1));

        // Act
        DiscountPolicyDTO dto = policy.toDTO();

        // Assert
        assertNotNull(dto);
        assertEquals("MAX", dto.getType());
        assertNotNull(dto.getChildren());
        assertEquals(1, dto.getChildren().size());
        assertEquals("SIMPLE", dto.getChildren().get(0).getType());
    }
}

