package unit.domain.Aggregates.event;

import domain.Aggregates.order.CustomerDetails;
import domain.Aggregates.productioncompany.ProductionCompany;
import domain.exceptions.PolicyEvaluationException;
import domain.policies.discount.IDiscountRule;
import domain.policies.purchase.IPurchaseRule;
import org.junit.jupiter.api.Test;

import domain.Aggregates.event.Event;
import domain.Aggregates.event.EventType;
import domain.Aggregates.event.Raffle;
import domain.Aggregates.event.RaffleStatus;
import domain.exceptions.InvalidEventException;
import domain.exceptions.RaffleException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.ExpectedCount.times;

class EventTest {

    // ── requiresRaffle ────────────────────────────────────────────────────────

    @Test
    void givenRaffleConfigured_whenRequiresRaffle_thenReturnsTrue() {
        Event event = Event.builder().raffle(Raffle.builder().build()).build();
        assertTrue(event.requiresRaffle());
    }

    @Test
    void givenNoRaffle_whenRequiresRaffle_thenReturnsFalse() {
        Event event = Event.builder().raffle(null).build();
        assertFalse(event.requiresRaffle());
    }

    // ── raffle root methods (issue #276) ──────────────────────────────────────

    private static Supplier<String> sequentialCodes() {
        AtomicInteger n = new AtomicInteger();
        return () -> "CODE" + n.incrementAndGet();
    }

    @Test
    void givenFutureDeadline_whenConfigureRaffle_thenRaffleAttachedOpen() {
        LocalDateTime now = LocalDateTime.now();
        Event event = Event.builder().eventId("e1").build();

        event.configureRaffle("r1", now.plusDays(1), Duration.ofMinutes(30), 1, now);

        assertTrue(event.requiresRaffle());
        assertEquals(RaffleStatus.OPEN, event.getRaffle().getStatus());
        assertEquals("e1", event.getRaffle().getEventId());
        assertEquals(now, event.getUpdatedAt());
    }

    @Test
    void givenPastDeadline_whenConfigureRaffle_thenThrowsInvalidEvent() {
        LocalDateTime now = LocalDateTime.now();
        Event event = Event.builder().eventId("e1").build();

        assertThrows(InvalidEventException.class,
                () -> event.configureRaffle("r1", now.minusMinutes(1), Duration.ofMinutes(30), 1, now));
    }

    @Test
    void givenNoRaffle_whenRegisterForRaffle_thenThrowsRaffleException() {
        Event event = Event.builder().eventId("e1").build();
        assertThrows(RaffleException.class,
                () -> event.registerForRaffle("user@mail.com", LocalDateTime.now()));
    }

    @Test
    void givenOpenRaffle_whenRegisterForRaffle_thenParticipantAdded() {
        LocalDateTime now = LocalDateTime.now();
        Event event = Event.builder().eventId("e1").build();
        event.configureRaffle("r1", now.plusDays(1), Duration.ofMinutes(30), 1, now);

        event.registerForRaffle("user@mail.com", now);

        assertTrue(event.getRaffleParticipants().contains("user@mail.com"));
    }

    @Test
    void givenDeadlinePassedAndOpen_whenIsRaffleDrawDue_thenTrue() {
        LocalDateTime now = LocalDateTime.now();
        Event event = Event.builder().eventId("e1").build();
        event.configureRaffle("r1", now.plusMinutes(1), Duration.ofMinutes(30), 1, now);

        assertFalse(event.isRaffleDrawDue(now));
        assertTrue(event.isRaffleDrawDue(now.plusMinutes(2)));
    }

    @Test
    void givenNoRaffle_whenIsRaffleDrawDue_thenFalse() {
        Event event = Event.builder().eventId("e1").build();
        assertFalse(event.isRaffleDrawDue(LocalDateTime.now()));
    }

    @Test
    void givenParticipantsAndCapacity_whenDrawRaffleWinners_thenCappedAtCapacity() {
        LocalDateTime now = LocalDateTime.now();
        Event event = Event.builder().eventId("e1").build();
        event.configureRaffle("r1", now.plusMinutes(1), Duration.ofMinutes(30), 1, now);
        event.registerForRaffle("a@mail.com", now);
        event.registerForRaffle("b@mail.com", now);

        Set<String> winners = event.drawRaffleWinners(sequentialCodes(), now.plusMinutes(2));

        assertEquals(1, winners.size());
        assertEquals(RaffleStatus.DRAWN, event.getRaffle().getStatus());
    }

    @Test
    void givenNoRaffle_whenDrawRaffleWinners_thenEmptySet() {
        Event event = Event.builder().eventId("e1").build();
        assertTrue(event.drawRaffleWinners(sequentialCodes(), LocalDateTime.now()).isEmpty());
    }

    // ── UC3: matchesSearchCriteria ────────────────────────────────────────────

    private Event buildActiveEvent() {
        return Event.builder()
                .name("Rock Festival")
                .artist("The Beatles")
                .productionCompanyName("LiveNation")
                .eventType(new EventType("Music", ""))
                .location("Tel Aviv")
                .minPrice(50.0)
                .maxPrice(200.0)
                .startDate(LocalDateTime.of(2026, 6, 1, 20, 0))
                .endDate(LocalDateTime.of(2026, 6, 1, 23, 0))
                .active(true)
                .build();
    }

    @Test
    void givenAllNullCriteria_whenMatchesSearchCriteria_thenReturnsTrue() {
        Event event = buildActiveEvent();
        assertTrue(event.matchesSearchCriteria(null, null, null, null, null, null, null, null),
                "Event with all null criteria should match any active event");
    }

    @Test
    void givenInactiveEvent_whenMatchesSearchCriteria_thenReturnsFalse() {
        Event event = buildActiveEvent().toBuilder().active(false).build();
        assertFalse(event.matchesSearchCriteria(null, null, null, null, null, null, null, null),
                "Inactive event should never match search");
    }

    @Test
    void givenNonMatchingCompanyName_whenMatchesSearchCriteria_thenReturnsFalse() {
        Event event = buildActiveEvent();
        assertFalse(event.matchesSearchCriteria("OtherCompany", null, null, null, null, null, null, null),
                "Non-matching company name should return false");
    }

    @Test
    void givenMatchingKeyword_whenMatchesSearchCriteria_thenReturnsTrue() {
        Event event = buildActiveEvent();
        assertTrue(event.matchesSearchCriteria(null, List.of("Rock"), null, null, null, null, null, null),
                "Keyword matching event name should return true");
    }

    @Test
    void givenEmptyKeywordsList_whenMatchesSearchCriteria_thenReturnsTrue() {
        Event event = buildActiveEvent();
        assertTrue(event.matchesSearchCriteria(null, List.of(), null, null, null, null, null, null),
                "Empty keywords list (not null) should match any active event, same as null");
    }

    @Test
    void givenMatchingArtistKeyword_whenMatchesSearchCriteria_thenReturnsTrue() {
        Event event = buildActiveEvent();
        assertTrue(event.matchesSearchCriteria(null, List.of("Beatles"), null, null, null, null, null, null),
                "Keyword matching artist name should return true");
    }

    @Test
    void givenNonMatchingCategory_whenMatchesSearchCriteria_thenReturnsFalse() {
        Event event = buildActiveEvent();
        assertFalse(event.matchesSearchCriteria(null, null, "Sports", null, null, null, null, null),
                "Non-matching category should return false");
    }

    @Test
    void givenPriceRangeDoesNotOverlapEvent_whenMatchesSearchCriteria_thenReturnsFalse() {
        Event event = buildActiveEvent(); // minPrice=50, maxPrice=200
        assertFalse(event.matchesSearchCriteria(null, null, null, null, null, null, 250.0, 500.0),
                "Price range entirely above event prices should not match");
    }

    @Test
    void givenMatchingAllExceptDate_whenMatchesSearchCriteria_thenReturnsFalse() {
        Event event = buildActiveEvent(); // startDate=2026-06-01
        assertFalse(event.matchesSearchCriteria(
                "LiveNation",
                List.of("Rock"),
                "Music",
                LocalDateTime.of(2026, 7, 1, 0, 0), // startDate AFTER event
                LocalDateTime.of(2026, 9, 1, 0, 0),
                "Tel Aviv",
                50.0, 200.0),
                "Should be false when all match except date range");
    }

    @Test
    void givenMatchingAllExceptLocation_whenMatchesSearchCriteria_thenReturnsFalse() {
        Event event = buildActiveEvent(); // location=Tel Aviv
        assertFalse(event.matchesSearchCriteria(
                "LiveNation",
                List.of("Rock"),
                "Music",
                LocalDateTime.of(2026, 5, 1, 0, 0),
                LocalDateTime.of(2026, 7, 1, 0, 0),
                "Jerusalem", // wrong location
                50.0, 200.0),
                "Should be false when all match except location");
    }

    @Test
    void givenMatchingAllExceptKeyword_whenMatchesSearchCriteria_thenReturnsFalse() {
        Event event = buildActiveEvent();
        assertFalse(event.matchesSearchCriteria(
                "LiveNation",
                List.of("Jazz"), // keyword does not match name or artist
                "Music",
                LocalDateTime.of(2026, 5, 1, 0, 0),
                LocalDateTime.of(2026, 7, 1, 0, 0),
                "Tel Aviv",
                50.0, 200.0),
                "Should be false when all match except keyword");
    }

    @Test
    void givenMatchingAllCriteria_whenMatchesSearchCriteria_thenReturnsTrue() {
        Event event = buildActiveEvent();
        assertTrue(event.matchesSearchCriteria(
                "LiveNation",
                List.of("Rock", "Beatles"),
                "Music",
                LocalDateTime.of(2026, 5, 1, 0, 0),
                LocalDateTime.of(2026, 7, 1, 0, 0),
                "Tel Aviv",
                50.0, 300.0),
                "Should be true when all criteria match");
    }

    @Test
    void givenEventAndCompany_whenAllPurchasePoliciesPass_thenDoesNotThrow() throws Exception {
        // Arrange
        IPurchaseRule mockPurchasePolicy = mock(IPurchaseRule.class);
        ProductionCompany mockCompany = mock(ProductionCompany.class);
        CustomerDetails context = CustomerDetails.builder().build();

        Event event = Event.builder().purchasePolicy(mockPurchasePolicy).build();

        // Act & Assert
        assertDoesNotThrow(() -> event.isPurchaseAllowed(context, mockCompany));
        verify(mockCompany).isPurchaseAllowed(context);
        verify(mockPurchasePolicy).evaluate(context);
    }

    @Test
    void givenEvent_whenCompanyPurchasePolicyFails_thenThrowsAndAbortsImmediately() throws Exception {
        // Arrange
        IPurchaseRule mockPurchasePolicy = mock(IPurchaseRule.class);
        ProductionCompany mockCompany = mock(ProductionCompany.class);
        CustomerDetails context = CustomerDetails.builder().build();

        Event event = Event.builder().purchasePolicy(mockPurchasePolicy).build();

        // Simulate company failing the purchase evaluation
        doThrow(new PolicyEvaluationException("Company limit restriction"))
                .when(mockCompany).isPurchaseAllowed(context);

        // Act & Assert
        PolicyEvaluationException exception = assertThrows(PolicyEvaluationException.class, () ->
                event.isPurchaseAllowed(context, mockCompany)
        );

        assertEquals("Company limit restriction", exception.getMessage());
        // Verify event policy was NEVER evaluated because company failed first
        verify(mockPurchasePolicy, never()).evaluate(context);
    }

    @Test
    void givenEvent_whenCompanyPassesButEventPurchasePolicyFails_thenThrowsException() throws Exception {
        // Arrange
        IPurchaseRule mockPurchasePolicy = mock(IPurchaseRule.class);
        ProductionCompany mockCompany = mock(ProductionCompany.class);
        CustomerDetails context = CustomerDetails.builder().build();

        Event event = Event.builder().purchasePolicy(mockPurchasePolicy).build();

        doNothing().when(mockCompany).isPurchaseAllowed(context);
        doThrow(new PolicyEvaluationException("Event sold out"))
                .when(mockPurchasePolicy).evaluate(context);

        // Act & Assert
        PolicyEvaluationException exception = assertThrows(PolicyEvaluationException.class, () ->
                event.isPurchaseAllowed(context, mockCompany)
        );

        assertEquals("Event sold out", exception.getMessage());
    }

    @Test
    void givenNullCompanyAndNullPurchasePolicy_whenIsPurchaseAllowed_thenDoesNotThrow() {
        // Arrange
        Event event = Event.builder().purchasePolicy(null).build();
        CustomerDetails context = CustomerDetails.builder().build();

        // Act & Assert
        assertDoesNotThrow(() -> event.isPurchaseAllowed(context, null));
    }

    // ── 🌟 NEW TESTS: calculateDiscountedPrice ───────────────────────────────

    @Test
    void givenDiscountPolicies_whenCalculated_thenAppliesAccumulatedDiscount() {
        // Arrange
        IDiscountRule mockDiscountPolicy = mock(IDiscountRule.class);
        ProductionCompany mockCompany = mock(ProductionCompany.class);
        CustomerDetails context = CustomerDetails.builder().build();

        Event event = Event.builder().discountPolicy(mockDiscountPolicy).build();

        when(mockCompany.evaluateDiscount(context)).thenReturn(10.0); // 10% company discount
        when(mockDiscountPolicy.evaluate(context)).thenReturn(15.0);  // 15% event discount

        // Act
        double finalPrice = event.calculateDiscountedPrice(200.0, context, mockCompany);

        // Assert: 10% + 15% = 25% discount on 200.0 -> 150.0
        assertEquals(150.0, finalPrice, 0.001);
    }

    @Test
    void givenDiscountPolicies_whenTotalDiscountExceedsOneHundred_thenCapsAtOneHundredPercent() {
        // Arrange
        IDiscountRule mockDiscountPolicy = mock(IDiscountRule.class);
        ProductionCompany mockCompany = mock(ProductionCompany.class);
        CustomerDetails context = CustomerDetails.builder().build();

        Event event = Event.builder().discountPolicy(mockDiscountPolicy).build();

        when(mockCompany.evaluateDiscount(context)).thenReturn(70.0);
        when(mockDiscountPolicy.evaluate(context)).thenReturn(40.0); // Total 110%

        // Act
        double finalPrice = event.calculateDiscountedPrice(100.0, context, mockCompany);

        // Assert: Capped at 100% discount, making the price completely free (0.0)
        assertEquals(0.0, finalPrice, 0.001);
    }

    @Test
    void givenDiscountPolicies_whenTotalDiscountIsZeroOrNegative_thenReturnsBasePrice() {
        // Arrange
        IDiscountRule mockDiscountPolicy = mock(IDiscountRule.class);
        ProductionCompany mockCompany = mock(ProductionCompany.class);
        CustomerDetails context = CustomerDetails.builder().build();

        Event event = Event.builder().discountPolicy(mockDiscountPolicy).build();

        when(mockCompany.evaluateDiscount(context)).thenReturn(0.0);
        when(mockDiscountPolicy.evaluate(context)).thenReturn(-5.0); // Negative percentage edge case

        // Act
        double finalPrice = event.calculateDiscountedPrice(150.0, context, mockCompany);

        // Assert: Discount <= 0, returns original price
        assertEquals(150.0, finalPrice, 0.001);
    }

    @Test
    void givenNullCompanyAndNullDiscountPolicy_whenCalculateDiscountedPrice_thenReturnsOriginalPrice() {
        // Arrange
        Event event = Event.builder().discountPolicy(null).build();
        CustomerDetails context = CustomerDetails.builder().build();

        // Act
        double finalPrice = event.calculateDiscountedPrice(120.0, context, null);

        // Assert
        assertEquals(120.0, finalPrice, 0.001);
    }
}



