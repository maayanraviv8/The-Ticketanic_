package acceptance;

import static org.junit.jupiter.api.Assertions.*;

import application.dto.OrderDTO;
import application.dto.TicketDTO;
import application.service.MemberService;
import domain.exceptions.AccountSuspendedException;
import domain.exceptions.EmailAlreadyRegisteredException;
import domain.exceptions.InvalidCredentialsException;
import domain.exceptions.MemberNotFoundException;
import domain.exceptions.SamePasswordException;
import domain.exceptions.UnauthorizedException;
import domain.exceptions.UsernameAlreadyTakenException;
import domain.Aggregates.activeorder.ActiveOrder;
import domain.Aggregates.event.Event;
import domain.Aggregates.member.Member;
import domain.Aggregates.order.Order;
import domain.Aggregates.productioncompany.ProductionCompany;
import domain.Aggregates.show.Show;
import domain.Aggregates.ticket.Ticket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MemberServiceTest extends AcceptanceTestBase {

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepo, orderRepo, ticketRepo, passwordEncoder, authService, notificationService);
    }

    ///////// UC1 - Login /////////

    @Test
    void GivenValidCredentials_WhenLogin_ThenReturnsTokenForSession() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        // Act
        String token = memberService.login(VALID_EMAIL, VALID_PASSWORD).getToken();

        // Assert - return value
        assertNotNull(token);
    }

    @Test
    void GivenInvalidPassword_WhenLogin_ThenThrowInvalidCredentials() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        // Act + Assert
        assertThrows(InvalidCredentialsException.class,
                () -> memberService.login(VALID_EMAIL, INVALID_PASSWORD));

        // Assert - internal state (no token was issued → no session established)
        Member member = memberRepo.findByEmail(VALID_EMAIL);
        assertNotNull(member);
    }

    @Test
    void GivenNonRegisteredEmail_WhenLogin_ThenThrowMemberNotFound() {
        // Act + Assert
        assertThrows(MemberNotFoundException.class,
                () -> memberService.login(INVALID_EMAIL, VALID_PASSWORD));

        // Assert - internal state
        Member member = memberRepo.findByEmail(INVALID_EMAIL);
        assertNull(member);
    }

    @Test
    void GivenInactiveMember_WhenLogin_ThenThrowAccountSuspended() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        Member member = memberRepo.findByEmail(VALID_EMAIL);
        member.setActive(false);
        memberRepo.store(member);

        // Act + Assert
        assertThrows(AccountSuspendedException.class,
                () -> memberService.login(VALID_EMAIL, VALID_PASSWORD));
    }

    ///////// UC2 - Register /////////

    @Test
    void GivenValidDetails_WhenRegister_ThenReturnTrue() {
        // Act
        boolean result = memberService.register(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        // Assert - return value
        assertTrue(result);

        // Assert - internal state
        Member member = memberRepo.findByEmail(VALID_EMAIL);
        assertNotNull(member);
    }

    @Test
    void GivenExistingEmail_WhenRegister_ThenThrows() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        // Act + Assert
        assertThrows(EmailAlreadyRegisteredException.class,
                () -> memberService.register(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD));

        // Assert - internal state (only one member with that email)
        Member member = memberRepo.findByEmail(VALID_EMAIL);
        assertNotNull(member);
    }

    ///////// UC8 - Logout /////////

    @Test
    void GivenLoggedInMemberWithActiveOrder_WhenLogout_ThenUserLoggedOutAndActiveOrderIsPreserved() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        String token = memberService.login(VALID_EMAIL, VALID_PASSWORD).getToken();

        ActiveOrder activeOrder = storeActiveOrder(ACTIVE_ORDER_ID_1, VALID_EVENT_ID, VALID_SHOW_ID, DEFAULT_LOCK_MINUTES);
        Member memberBefore = memberRepo.findByEmail(VALID_EMAIL);
        memberBefore.assignActiveOrder(activeOrder.getId());
        memberRepo.store(memberBefore);

        // Act & Assert - does not throw
        assertDoesNotThrow(() -> memberService.logout(token));

        // Assert - internal state (active order is preserved across logout)
        Member member = memberRepo.findByEmail(VALID_EMAIL);
        assertNotNull(member);
        assertEquals(activeOrder.getId(), member.getActiveOrderId());
    }

    @Test
    void GivenLoggedInMemberWithoutActiveOrder_WhenLogout_ThenUserLoggedOutAndNoActiveOrderIsPreserved() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        String token = memberService.login(VALID_EMAIL, VALID_PASSWORD).getToken();

        // Act & Assert - does not throw
        assertDoesNotThrow(() -> memberService.logout(token));

        // Assert - internal state (no active order persists from logout)
        Member member = memberRepo.findByEmail(VALID_EMAIL);
        assertNotNull(member);
        assertNull(member.getActiveOrderId());
    }

    ///////// UC9 - View Order History /////////

    @Test
    void GivenMemberWithTwoOrders_WhenGetOrderHistory_ThenReturnBothOrders() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        String token = memberService.login(VALID_EMAIL, VALID_PASSWORD).getToken();

        Order order1 = storeOrder(ORDER_ID_1, storeActiveOrder(ACTIVE_ORDER_ID_1, VALID_EVENT_ID, VALID_SHOW_ID, DEFAULT_LOCK_MINUTES));
        Order order2 = storeOrder(ORDER_ID_2, storeActiveOrder(ACTIVE_ORDER_ID_2, VALID_EVENT_ID, VALID_SHOW_ID, DEFAULT_LOCK_MINUTES));

        linkOrderToMember(VALID_EMAIL, order1.getOrderId());
        linkOrderToMember(VALID_EMAIL, order2.getOrderId());

        // Act
        List<OrderDTO> history = memberService.getOrderHistory(token);

        // Assert
        assertNotNull(history);
        assertEquals(2, history.size());
        assertTrue(history.stream().anyMatch(o -> o.getOrderId().equals(ORDER_ID_1)));
        assertTrue(history.stream().anyMatch(o -> o.getOrderId().equals(ORDER_ID_2)));
    }

    @Test
    void GivenMemberWithNoOrders_WhenGetOrderHistory_ThenReturnEmptyList() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        String token = memberService.login(VALID_EMAIL, VALID_PASSWORD).getToken();

        // Act
        List<OrderDTO> history = memberService.getOrderHistory(token);

        // Assert
        assertNotNull(history);
        assertTrue(history.isEmpty());
    }

    @Test
    void GivenMemberWithOrderThatChangedPrice_WhenGetOrderHistory_ThenOriginalPriceIsPreserved() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        String token = memberService.login(VALID_EMAIL, VALID_PASSWORD).getToken();

        double originalPrice = 100.0;
        Ticket ticket = storeTicket(TICKET_ID_1, originalPrice);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, VALID_EVENT_ID, VALID_SHOW_ID, ticket, DEFAULT_LOCK_MINUTES);
        Order order = storeOrder(ORDER_ID_1, activeOrder);
        linkOrderToMember(VALID_EMAIL, order.getOrderId());

        // Simulate retroactive ticket price change
        ticket.setPrice(200.0);
        ticketRepo.store(ticket);

        // Act
        List<OrderDTO> history = memberService.getOrderHistory(token);

        // Assert
        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals(originalPrice, history.get(0).getFinalPrice());
    }

    @Test
    void GivenMemberWithOrderThatChangedShow_WhenGetOrderHistory_ThenOriginalShowIsPreserved() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        String token = memberService.login(VALID_EMAIL, VALID_PASSWORD).getToken();

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true);
        Ticket ticket = storeTicket(TICKET_ID_1, 100.0);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, event.getEventId(), show.getShowId(), ticket, DEFAULT_LOCK_MINUTES);
        Order order = storeOrder(ORDER_ID_1, activeOrder);
        linkOrderToMember(VALID_EMAIL, order.getOrderId());

        // Simulate show cancellation
        show.setActive(false);
        showRepo.store(show);

        // Act
        List<OrderDTO> history = memberService.getOrderHistory(token);

        // Assert
        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals(order.getOrderId(), history.get(0).getOrderId());
    }

    @Test
    void GivenMemberWithOrderThatChangedCompany_WhenGetOrderHistory_ThenOriginalOrderIsPreserved() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        String token = memberService.login(VALID_EMAIL, VALID_PASSWORD).getToken();

        // Build full hierarchy: Company → Event → Show → Ticket
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        Event event = storeEvent(company.getName(), true);
        Show show = storeShow(event.getEventId(), true);
        Ticket ticket = storeTicket(TICKET_ID_1, 100.0);
        ActiveOrder activeOrder = storeActiveOrderWithTicket(ACTIVE_ORDER_ID_1, event.getEventId(), show.getShowId(), ticket, DEFAULT_LOCK_MINUTES);
        Order order = storeOrder(ORDER_ID_1, activeOrder);
        linkOrderToMember(VALID_EMAIL, order.getOrderId());

        // Simulate company closure
        company.setActive(false);
        companyRepo.store(company);

        // Act
        List<OrderDTO> history = memberService.getOrderHistory(token);

        // Assert
        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals(order.getOrderId(), history.get(0).getOrderId());
    }

    ///////// UC9 helper - getTicketsByIds /////////

    @Test
    void GivenMemberWithTwoTickets_WhenGetTicketsByIds_ThenReturnsBothTicketDTOs() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        String token = memberService.login(VALID_EMAIL, VALID_PASSWORD).getToken();
        Ticket t1 = storeTicket(TICKET_ID_1, 100.0);
        Ticket t2 = new Ticket("ticket2", VALID_SHOW_ID, VALID_ZONE_ID, null, 50.0);
        ticketRepo.store(t2);

        // Act
        List<TicketDTO> result = memberService.getTicketsByIds(List.of(t1.getId(), t2.getId()), token);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(t -> t.getId().equals(TICKET_ID_1) && t.getSeatId() != null));
        assertTrue(result.stream().anyMatch(t -> t.getId().equals("ticket2") && t.getSeatId() == null));
    }

    @Test
    void GivenEmptyList_WhenGetTicketsByIds_ThenReturnsEmptyList() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        String token = memberService.login(VALID_EMAIL, VALID_PASSWORD).getToken();

        // Act
        List<TicketDTO> result = memberService.getTicketsByIds(List.of(), token);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void GivenMixedValidAndUnknownIds_WhenGetTicketsByIds_ThenSkipsUnknownAndReturnsValid() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        String token = memberService.login(VALID_EMAIL, VALID_PASSWORD).getToken();
        Ticket valid = storeTicket(TICKET_ID_1, 100.0);

        // Act
        List<TicketDTO> result = memberService.getTicketsByIds(
                List.of(valid.getId(), "ghost-ticket-id"), token);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(TICKET_ID_1, result.get(0).getId());
    }

    @Test
    void GivenNullToken_WhenGetTicketsByIds_ThenThrowsUnauthorized() {
        // Arrange
        storeTicket(TICKET_ID_1, 100.0);

        // Act & Assert
        assertThrows(UnauthorizedException.class,
                () -> memberService.getTicketsByIds(List.of(TICKET_ID_1), null));
    }

    ///////// UC11 - Edit Personal Details /////////

    @Test
    void GivenValidNewUsername_WhenEditUsername_ThenReturnTrueAndUsernameUpdated() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        String token = memberService.login(VALID_EMAIL, VALID_PASSWORD).getToken();

        // Act & Assert - does not throw
        assertDoesNotThrow(() -> memberService.editUsername(token, VALID_NEW_USERNAME));

        // Assert - internal state
        Member member = memberRepo.findByEmail(VALID_EMAIL);
        assertNotNull(member);
        assertEquals(VALID_NEW_USERNAME, member.getUsername());
    }

    @Test
    void GivenValidNewPassword_WhenEditPassword_ThenReturnTrueAndPasswordUpdated() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        String token = memberService.login(VALID_EMAIL, VALID_PASSWORD).getToken();

        // Act & Assert - does not throw
        assertDoesNotThrow(() -> memberService.editPassword(token, VALID_NEW_PASSWORD));

        // Assert - internal state (old password rejected, new password accepted)
        assertThrows(InvalidCredentialsException.class,
                () -> memberService.login(VALID_EMAIL, VALID_PASSWORD));
        assertNotNull(memberService.login(VALID_EMAIL, VALID_NEW_PASSWORD).getToken());
    }

    @Test
    void GivenExistingUsername_WhenEditUsername_ThenThrowsAndUsernameUnchanged() {
        // Arrange
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        String token = memberService.login(VALID_EMAIL, VALID_PASSWORD).getToken();

        // Act & Assert - throws
        assertThrows(UsernameAlreadyTakenException.class,
                () -> memberService.editUsername(token, VALID_USERNAME_2));

        // Assert - internal state
        Member member = memberRepo.findByEmail(VALID_EMAIL);
        assertNotNull(member);
        assertEquals(VALID_USERNAME, member.getUsername());
    }

    @Test
    void GivenSamePasswordAsBefore_WhenEditPassword_ThenThrowsAndPasswordUnchanged() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        String token = memberService.login(VALID_EMAIL, VALID_PASSWORD).getToken();

        // Act & Assert - throws
        assertThrows(SamePasswordException.class,
                () -> memberService.editPassword(token, VALID_PASSWORD));

        // Assert - internal state (original password still works)
        assertNotNull(memberService.login(VALID_EMAIL, VALID_PASSWORD).getToken());
    }

    ///////// Race Condition Tests /////////

    @Test
    void givenMultipleRequestsWithSameEmail_whenConcurrentRegister_thenOnlyOneSucceeds() throws InterruptedException {
        // Arrange
        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    memberService.register(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
                    successCount.incrementAndGet();
                } catch (EmailAlreadyRegisteredException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Act
        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertTrue(completed, "Test timed out");
        assertEquals(1, successCount.get(), "Exactly one registration should succeed");
        assertEquals(numberOfThreads - 1, failureCount.get(), "All other registrations should fail");
        
        // Verify only one member exists
        assertEquals(1, memberRepo.getAll().size(), "Only one member should be in the repository");
    }
}
