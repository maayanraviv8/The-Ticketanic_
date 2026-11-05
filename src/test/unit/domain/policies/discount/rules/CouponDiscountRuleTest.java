package unit.domain.policies.discount.rules;

import application.dto.DiscountPolicyDTO;
import domain.Aggregates.order.CustomerDetails;
import domain.policies.discount.rules.CouponDiscountRule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CouponDiscountRuleTest {

    @Test
    void givenCouponDiscount_whenValidCouponProvided_thenReturnsDiscount() {
        // Arrange
        LocalDateTime expiryDate = LocalDateTime.of(2026, 12, 31, 23, 59);
        CouponDiscountRule rule = new CouponDiscountRule("SUMMER20", 20.0, expiryDate);
        CustomerDetails context = CustomerDetails.builder()
                .couponCode("SUMMER20")
                .build();

        // Act
        double discount = rule.evaluate(context);

        // Assert
        assertEquals(20.0, discount);
    }

    @Test
    void givenCouponDiscount_whenWrongCouponProvided_thenReturnsZero() {
        // Arrange
        LocalDateTime expiryDate = LocalDateTime.of(2026, 12, 31, 23, 59);
        CouponDiscountRule rule = new CouponDiscountRule("SUMMER20", 20.0, expiryDate);
        CustomerDetails context = CustomerDetails.builder()
                .couponCode("WRONGCODE")
                .build();

        // Act
        double discount = rule.evaluate(context);

        // Assert
        assertEquals(0.0, discount);
    }

    @Test
    void givenCouponDiscount_whenNoCouponProvided_thenReturnsZero() {
        // Arrange
        LocalDateTime expiryDate = LocalDateTime.of(2026, 12, 31, 23, 59);
        CouponDiscountRule rule = new CouponDiscountRule("SUMMER20", 20.0, expiryDate);
        CustomerDetails context = CustomerDetails.builder()
                .build();

        // Act
        double discount = rule.evaluate(context);

        // Assert
        assertEquals(0.0, discount);
    }

    @Test
    void givenCouponDiscount_whenCouponExpired_thenReturnsZero() {
        // Arrange
        LocalDateTime expiryDate = LocalDateTime.of(2020, 12, 31, 23, 59);
        CouponDiscountRule rule = new CouponDiscountRule("SUMMER20", 20.0, expiryDate);
        CustomerDetails context = CustomerDetails.builder()
                .couponCode("SUMMER20")
                .build();

        // Act
        double discount = rule.evaluate(context);

        // Assert
        assertEquals(0.0, discount);
    }


    @Test
    void givenCouponDiscount_thenDescriptionIsCorrect() {
        // Arrange
        LocalDateTime expiryDate = LocalDateTime.of(2026, 12, 31, 23, 59);
        CouponDiscountRule rule = new CouponDiscountRule("PROMO25", 25.0, expiryDate);

        // Act
        String description = rule.getDescription();

        // Assert
        assertTrue(description.contains("PROMO25"));
        assertTrue(description.contains("25.0"));
        assertTrue(description.contains("2026"));
    }

    @Test
    void givenCouponDiscount_whenToDTO_thenDTOMatchedCorrectly() {
        // Arrange
        LocalDateTime expiry = LocalDateTime.now().plusDays(5);
        CouponDiscountRule rule = new CouponDiscountRule( "SAVE20", 20.0, expiry);

        // Act
        DiscountPolicyDTO dto = rule.toDTO();

        // Assert
        assertNotNull(dto);
        assertEquals("COUPON", dto.getType());
        assertEquals(20.0, dto.getPercentage());
        assertEquals("SAVE20", dto.getCouponCode());
        assertEquals(expiry, dto.getValidUntil());
    }
}

