package acceptance;

import domain.Aggregates.activeorder.ActiveOrder;
import domain.Aggregates.event.Event;
import domain.Aggregates.member.Member;
import domain.Aggregates.show.Seat;
import domain.Aggregates.show.Show;
import domain.Aggregates.show.VenueMap;
import domain.Aggregates.show.Zone;
import domain.Aggregates.ticket.Ticket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import application.dto.ActiveOrderDTO;
import application.service.ActiveOrderService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

import domain.Aggregates.virtualqueue.VirtualQueue;
import domain.exceptions.MemberSuspendedException;

public class ActiveOrderServiceTest extends AcceptanceTestBase {

    private ActiveOrderService activeOrderService;

    @BeforeEach
    void setUp(){
        activeOrderService = new ActiveOrderService(activeOrderRepo, eventRepo, showRepo, ticketRepo, memberRepo, virtualQueueRepo, authService, TEST_ACTIVE_ORDER_PROPERTIES);
    }

    ///////// UC5 - Reserve Tickets /////////

    @Test
    void givenLoggedInMemberAndAvailableSeat_whenReserveTickets_thenCreatesActiveOrderAndLinksToMember() {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        String showId = show.getShowId();

        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        grantQueueAccess(showId, VALID_EMAIL);

        // Act
        ActiveOrderDTO result = assertDoesNotThrow(() ->
                activeOrderService.reserveTickets(showId, token, null, null, VALID_ZONE_ID, List.of(VALID_SEAT_ID), 0));

        // Assert - order is stored and contains the reserved ticket
        assertNotNull(activeOrderRepo.findById(result.getId()));
        assertEquals(1, result.getTicketIds().size());
        Ticket ticket = ticketRepo.findById(result.getTicketIds().get(0));
        assertEquals(VALID_SEAT_ID, ticket.getSeatId());
        assertEquals(VALID_ZONE_ID, ticket.getZoneId());
        assertEquals(showId, ticket.getShowId());

        // Assert - order is linked to the member
        Member member = memberRepo.findByEmail(VALID_EMAIL);
        assertEquals(result.getId(), member.getActiveOrderId());

        // Assert - seat is now locked in the VenueMap
        Show updatedShow = showRepo.findById(showId);
        assertTrue(updatedShow.getVenueMap().getSeatById(VALID_ZONE_ID, VALID_SEAT_ID).isTaken());
    }

    @Test
    void givenGuestWithQueueSlotAndAvailableSeat_whenReserveTickets_thenCreatesActiveOrderWithoutMemberLink() {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        String showId = show.getShowId();
        String guestId = "guest-session-1";
        grantQueueAccess(showId, guestId);

        // Act - no token, guest identifier carries the queue slot
        ActiveOrderDTO result = assertDoesNotThrow(() ->
                activeOrderService.reserveTickets(showId, null, guestId, null, VALID_ZONE_ID, List.of(VALID_SEAT_ID), 0));

        // Assert - order is stored and contains the reserved ticket
        assertNotNull(activeOrderRepo.findById(result.getId()));
        assertEquals(1, result.getTicketIds().size());
        Ticket ticket = ticketRepo.findById(result.getTicketIds().get(0));
        assertEquals(VALID_SEAT_ID, ticket.getSeatId());
        assertEquals(VALID_ZONE_ID, ticket.getZoneId());
        assertEquals(showId, ticket.getShowId());

        // Assert - seat is now locked in the VenueMap
        Show updatedShow = showRepo.findById(showId);
        assertTrue(updatedShow.getVenueMap().getSeatById(VALID_ZONE_ID, VALID_SEAT_ID).isTaken());
    }

    @Test
    void givenGuestWithoutQueueSlot_whenReserveTickets_thenThrowsAndNoOrderCreated() {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        String showId = show.getShowId();
        String guestId = "guest-bypass-attempt";
        // Note: no grantQueueAccess — the guest tries to skip the queue.

        // Act & Assert — backend rejects guests who didn't go through the queue UI
        assertThrows(Exception.class, () ->
                activeOrderService.reserveTickets(showId, null, guestId, null, VALID_ZONE_ID, List.of(VALID_SEAT_ID), 0));

        // Assert - no order created, seat still free
        assertEquals(0, activeOrderRepo.getAll().size());
        Show updatedShow = showRepo.findById(showId);
        assertFalse(updatedShow.getVenueMap().getSeatById(VALID_ZONE_ID, VALID_SEAT_ID).isTaken());
    }

    @Test
    void givenLoggedInMemberAndValidRaffleCode_whenReserveTickets_thenCreatesActiveOrderAndLinksToMember() {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEventWithDrawnRaffle(VALID_COMPANY_NAME, true, VALID_EMAIL, VALID_RAFFLE_CODE);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        String showId = show.getShowId();

        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        // Act - the winner's valid code grants entry; the raffle gate replaces the virtual queue
        ActiveOrderDTO result = assertDoesNotThrow(() ->
                activeOrderService.reserveTickets(showId, token, null, VALID_RAFFLE_CODE, VALID_ZONE_ID, List.of(VALID_SEAT_ID), 0));

        // Assert - order is stored and contains the reserved ticket
        assertNotNull(activeOrderRepo.findById(result.getId()));
        assertEquals(1, result.getTicketIds().size());
        Ticket ticket = ticketRepo.findById(result.getTicketIds().get(0));
        assertEquals(VALID_SEAT_ID, ticket.getSeatId());
        assertEquals(VALID_ZONE_ID, ticket.getZoneId());
        assertEquals(showId, ticket.getShowId());

        // Assert - order is linked to the member
        Member member = memberRepo.findByEmail(VALID_EMAIL);
        assertEquals(result.getId(), member.getActiveOrderId());

        // Assert - seat is now locked in the VenueMap
        Show updatedShow = showRepo.findById(showId);
        assertTrue(updatedShow.getVenueMap().getSeatById(VALID_ZONE_ID, VALID_SEAT_ID).isTaken());
    }


    @Test
    void givenLoggedInMemberAndAvailableStandingZone_whenReserveTickets_thenCreatesActiveOrderWith2Tickets() {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithStandingZone(event.getEventId(), VALID_ZONE_ID, 5);
        String showId = show.getShowId();

        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        grantQueueAccess(showId, VALID_EMAIL);

        // Act
        ActiveOrderDTO result = assertDoesNotThrow(() ->
                activeOrderService.reserveTickets(showId, token, null, null, VALID_ZONE_ID, null, 2));

        // Assert - order is stored with 2 standing tickets
        assertNotNull(activeOrderRepo.findById(result.getId()));
        assertEquals(2, result.getTicketIds().size());
        Ticket ticket = ticketRepo.findById(result.getTicketIds().get(0));
        assertEquals(VALID_ZONE_ID, ticket.getZoneId());
        assertEquals(showId, ticket.getShowId());
        assertTrue(ticket.isStanding());
        ticket = ticketRepo.findById(result.getTicketIds().get(1));
        assertEquals(VALID_ZONE_ID, ticket.getZoneId());
        assertEquals(showId, ticket.getShowId());
        assertTrue(ticket.isStanding());

        // Assert - order is linked to the member
        Member member = memberRepo.findByEmail(VALID_EMAIL);
        assertEquals(result.getId(), member.getActiveOrderId());

        // Assert - standing capacity decreased by 2 in the VenueMap
        Show updatedShow = showRepo.findById(showId);
        Zone zone = updatedShow.getVenueMap().getZoneById(VALID_ZONE_ID);
        assertEquals(3, zone.getStandingAvailable());
    }

    @Test
    void givenInvalidRaffleCode_whenReserveTickets_thenThrowsAndNoOrderCreated() {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEventWithDrawnRaffle(VALID_COMPANY_NAME, true, VALID_EMAIL, VALID_RAFFLE_CODE);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        String showId = show.getShowId();

        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        // Act & Assert - a wrong code is rejected
        assertThrows(Exception.class, () ->
                activeOrderService.reserveTickets(showId, token, null, INVALID_RAFFLE_CODE, VALID_ZONE_ID, List.of(VALID_SEAT_ID), 0));

        // Assert - no order was created
        assertEquals(0, activeOrderRepo.getAll().size());

        // Assert - seat is still available
        Show updatedShow = showRepo.findById(showId);
        assertFalse(updatedShow.getVenueMap().getSeatById(VALID_ZONE_ID, VALID_SEAT_ID).isTaken());

        // Assert - member has no active order
        Member member = memberRepo.findByEmail(VALID_EMAIL);
        assertNull(member.getActiveOrderId());
    }


    @Test
    void givenGuest_whenReserveRaffleEvent_thenThrowsAndNoOrderCreated() {
        // Arrange - raffle event, no token, only a guest id
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEventWithDrawnRaffle(VALID_COMPANY_NAME, true, VALID_EMAIL, VALID_RAFFLE_CODE);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        String showId = show.getShowId();

        // Act & Assert - raffle events require a logged-in member
        assertThrows(Exception.class, () ->
                activeOrderService.reserveTickets(showId, null, "guest-1", VALID_RAFFLE_CODE, VALID_ZONE_ID, List.of(VALID_SEAT_ID), 0));
        assertEquals(0, activeOrderRepo.getAll().size());
        assertFalse(showRepo.findById(showId).getVenueMap().getSeatById(VALID_ZONE_ID, VALID_SEAT_ID).isTaken());
    }

    @Test
    void givenSaleNotOpen_whenReserveRaffleEvent_thenThrowsAndNoOrderCreated() {
        // Arrange - raffle still OPEN (registration deadline in the future, no draw yet)
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEventWithRaffle(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        String showId = show.getShowId();

        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        // Act & Assert - tickets are not on sale until the draw happens
        assertThrows(Exception.class, () ->
                activeOrderService.reserveTickets(showId, token, null, "ANYCODE", VALID_ZONE_ID, List.of(VALID_SEAT_ID), 0));
        assertEquals(0, activeOrderRepo.getAll().size());
        assertFalse(showRepo.findById(showId).getVenueMap().getSeatById(VALID_ZONE_ID, VALID_SEAT_ID).isTaken());
    }

    @Test
    void givenNonWinner_whenReserveRaffleEvent_thenThrowsAndNoOrderCreated() {
        // Arrange - drawn raffle where VALID_EMAIL_2 won; VALID_EMAIL did not
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEventWithDrawnRaffle(VALID_COMPANY_NAME, true, VALID_EMAIL_2, VALID_RAFFLE_CODE);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        String showId = show.getShowId();

        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        // Act & Assert - a non-winner cannot reserve, even with someone else's code
        assertThrows(Exception.class, () ->
                activeOrderService.reserveTickets(showId, token, null, VALID_RAFFLE_CODE, VALID_ZONE_ID, List.of(VALID_SEAT_ID), 0));
        assertEquals(0, activeOrderRepo.getAll().size());
        assertFalse(showRepo.findById(showId).getVenueMap().getSeatById(VALID_ZONE_ID, VALID_SEAT_ID).isTaken());
    }

    @Test
    void givenNonExistentShow_whenReserveTickets_thenThrowsAndNoOrderCreated() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        // Act & Assert
        assertThrows(Exception.class, () ->
                activeOrderService.reserveTickets("invalid_show", token, null, null, VALID_ZONE_ID, List.of(VALID_SEAT_ID), 0));

        // Assert - no order was created
        assertEquals(0, activeOrderRepo.getAll().size());

        // Assert - member has no active order
        Member member = memberRepo.findByEmail(VALID_EMAIL);
        assertNull(member.getActiveOrderId());
    }

    @Test
    void givenSeatAlreadyTaken_whenReserveTickets_thenThrowsAndNoOrderCreated() {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        String showId = show.getShowId();

        // Lock the seat directly in the repo
        show.getVenueMap().getSeatById(VALID_ZONE_ID, VALID_SEAT_ID).setTaken(true);
        showRepo.store(show);

        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        grantQueueAccess(showId, VALID_EMAIL);

        // Act & Assert
        assertThrows(Exception.class, () ->
                activeOrderService.reserveTickets(showId, token, null, null, VALID_ZONE_ID, List.of(VALID_SEAT_ID), 0));

        // Assert - no order was created
        assertEquals(0, activeOrderRepo.getAll().size());

        // Assert - seat remains locked 
        Show updatedShow = showRepo.findById(showId);
        assertTrue(updatedShow.getVenueMap().getSeatById(VALID_ZONE_ID, VALID_SEAT_ID).isTaken());

        // Assert - member has no active order
        Member member = memberRepo.findByEmail(VALID_EMAIL);
        assertNull(member.getActiveOrderId());
    }

    @Test
    void givenMemberAlreadyHasActiveOrder_whenReserveTickets_thenThrowsAndOriginalOrderUnchanged() {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        String showId = show.getShowId();

        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        grantQueueAccess(showId, VALID_EMAIL);

        // Set up existing active order linked to member
        ActiveOrder existingOrder = storeActiveOrder(ACTIVE_ORDER_ID_1, event.getEventId(), showId, DEFAULT_LOCK_MINUTES);
        Member member = memberRepo.findByEmail(VALID_EMAIL);
        member.assignActiveOrder(existingOrder.getId());
        memberRepo.store(member);

        // Act & Assert - member tries to open a second order
        assertThrows(Exception.class, () ->
                activeOrderService.reserveTickets(showId, token, null, null, VALID_ZONE_ID, List.of(VALID_SEAT_ID), 0));

        // Assert - still only one order exists
        assertEquals(1, activeOrderRepo.getAll().size());

        // Assert - member still linked to the original order
        Member updatedMember = memberRepo.findByEmail(VALID_EMAIL);
        assertEquals(existingOrder.getId(), updatedMember.getActiveOrderId());
    }

    @Test
    void givenSuspendedMember_whenReserveTickets_thenThrowsMemberSuspendedException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        suspendMember(VALID_EMAIL);
        Show show = storeShowWithStandingZone(VALID_EVENT_ID, VALID_ZONE_ID, 10);

        // Act + Assert
        assertThrows(MemberSuspendedException.class,
                () -> activeOrderService.reserveTickets(show.getShowId(), token, null, null, VALID_ZONE_ID, null, 1));

        // Assert - internal state
        assertTrue(activeOrderRepo.getAll().isEmpty());
        assertTrue(memberRepo.findByEmail(VALID_EMAIL).isSuspended());
    }

    @Test
    void givenMemberWasSuspendedAndThenUnsuspended_whenReserveTickets_thenNotBlockedBySuspension() {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithStandingZone(event.getEventId(), VALID_ZONE_ID, 5);
        String showId = show.getShowId();

        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        grantQueueAccess(showId, VALID_EMAIL);

        // Suspend and unsuspend the member
        suspendMember(VALID_EMAIL);
        unsuspendMember(VALID_EMAIL);

        // Act
        ActiveOrderDTO result = assertDoesNotThrow(() ->
                activeOrderService.reserveTickets(showId, token, null, null, VALID_ZONE_ID, null, 2));

        // Assert - order is stored with 2 standing tickets
        assertNotNull(activeOrderRepo.findById(result.getId()));
        assertEquals(2, result.getTicketIds().size());
        Ticket ticket = ticketRepo.findById(result.getTicketIds().get(0));
        assertEquals(VALID_ZONE_ID, ticket.getZoneId());
        assertEquals(showId, ticket.getShowId());
        assertTrue(ticket.isStanding());
        ticket = ticketRepo.findById(result.getTicketIds().get(1));
        assertEquals(VALID_ZONE_ID, ticket.getZoneId());
        assertEquals(showId, ticket.getShowId());
        assertTrue(ticket.isStanding());

        // Assert - order is linked to the member
        Member member = memberRepo.findByEmail(VALID_EMAIL);
        assertEquals(result.getId(), member.getActiveOrderId());

        // Assert - standing capacity decreased by 2 in the VenueMap
        Show updatedShow = showRepo.findById(showId);
        Zone zone = updatedShow.getVenueMap().getZoneById(VALID_ZONE_ID);
        assertEquals(3, zone.getStandingAvailable());
    }

    ///////// UC6 - Edit Active Order (remove ticket) /////////

    @Test
    void givenOrderWithTwoTickets_whenRemoveOne_thenOrderKeepsOtherAndSeatIsReleased() throws Exception {
        // Arrange — reserve two seats so the order has two tickets and both seats are locked
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        Seat seatA = Seat.builder().id("seatA").row(1).number(1).price(50.0).build();
        Seat seatB = Seat.builder().id("seatB").row(1).number(2).price(50.0).build();
        Zone zone = Zone.builder().zoneId(VALID_ZONE_ID).standing(false).build();
        zone.addSeat(seatA);
        zone.addSeat(seatB);
        Show show = Show.builder().eventId(event.getEventId()).active(true).build();
        showRepo.store(show);
        VenueMap venueMap = VenueMap.builder().showId(show.getShowId()).build();
        venueMap.addZone(zone);
        show.setVenueMap(venueMap);
        showRepo.store(show);
        String showId = show.getShowId();

        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        grantQueueAccess(showId, VALID_EMAIL);
        ActiveOrderDTO created = activeOrderService.reserveTickets(
                showId, token, null, null, VALID_ZONE_ID, List.of("seatA", "seatB"), 0);
        String orderId = created.getId();
        String removedTicketId = created.getTicketIds().get(0);
        String keptTicketId = created.getTicketIds().get(1);

        // Act
        boolean removed = activeOrderService.removeTicketFromOrder(orderId, removedTicketId);

        // Assert — service result and order state
        assertTrue(removed);
        ActiveOrder updatedOrder = activeOrderRepo.findById(orderId);
        assertEquals(1, updatedOrder.getTicketIds().size());
        assertEquals(keptTicketId, updatedOrder.getTicketIds().get(0));

        // Assert — the removed ticket's seat is back to available, the kept ticket's seat is still taken
        Show updatedShow = showRepo.findById(showId);
        Ticket removedTicket = ticketRepo.findById(removedTicketId);
        assertNull(removedTicket, "removed ticket should be deleted from the repo");
        // We can no longer ask the deleted ticket for its zone/seat — assert via known seat ids
        assertFalse(updatedShow.getVenueMap().getSeatById(VALID_ZONE_ID, "seatA").isTaken());
        assertTrue(updatedShow.getVenueMap().getSeatById(VALID_ZONE_ID, "seatB").isTaken());
    }

    @Test
    void givenOrderThatDoesNotContainTicket_whenRemove_thenThrowsAndOrderUnchanged() throws Exception {
        // Arrange — reserve one seat so the order exists with exactly one ticket
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        String showId = show.getShowId();
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        grantQueueAccess(showId, VALID_EMAIL);
        ActiveOrderDTO created = activeOrderService.reserveTickets(
                showId, token, null, null, VALID_ZONE_ID, List.of(VALID_SEAT_ID), 0);
        String orderId = created.getId();
        String ticketIdInOrder = created.getTicketIds().get(0);

        // Act + Assert — removing a ticket id the order doesn't hold throws
        assertThrows(Exception.class,
                () -> activeOrderService.removeTicketFromOrder(orderId, "ticket-not-in-order"));

        // Assert — order is unchanged
        ActiveOrder unchanged = activeOrderRepo.findById(orderId);
        assertEquals(1, unchanged.getTicketIds().size());
        assertEquals(ticketIdInOrder, unchanged.getTicketIds().get(0));
        assertNotNull(ticketRepo.findById(ticketIdInOrder));
        assertTrue(showRepo.findById(showId).getVenueMap()
                .getSeatById(VALID_ZONE_ID, VALID_SEAT_ID).isTaken());
    }

    @Test
    void givenNoActiveOrderForId_whenRemoveTicket_thenThrowsAndNoRepoChange() {
        // Arrange — nothing in the active-order repo for this id
        String missingOrderId = "no-such-order";
        int activeOrdersBefore = activeOrderRepo.getAll().size();
        int ticketsBefore = ticketRepo.getAll().size();

        // Act + Assert
        assertThrows(Exception.class,
                () -> activeOrderService.removeTicketFromOrder(missingOrderId, TICKET_ID_1));

        assertEquals(activeOrdersBefore, activeOrderRepo.getAll().size());
        assertEquals(ticketsBefore, ticketRepo.getAll().size());
    }

    ///////// addTicketsToOrder (extended UC6 add-tickets flow) /////////

    @Test
    void givenExistingOrderInZoneA_whenAddAnotherSeatFromZoneA_thenOrderGrowsAndSeatLocks() throws Exception {
        // Arrange — show with two seats in the same zone; reserve one
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        Seat seatA1 = Seat.builder().id("a1").row(1).number(1).price(50.0).build();
        Seat seatA2 = Seat.builder().id("a2").row(1).number(2).price(50.0).build();
        Zone zoneA = Zone.builder().zoneId(VALID_ZONE_ID).standing(false).build();
        zoneA.addSeat(seatA1);
        zoneA.addSeat(seatA2);
        Show show = Show.builder().eventId(event.getEventId()).active(true).build();
        showRepo.store(show);
        VenueMap venueMap = VenueMap.builder().showId(show.getShowId()).build();
        venueMap.addZone(zoneA);
        show.setVenueMap(venueMap);
        showRepo.store(show);
        String showId = show.getShowId();

        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        grantQueueAccess(showId, VALID_EMAIL);
        ActiveOrderDTO created = activeOrderService.reserveTickets(
                showId, token, null, null, VALID_ZONE_ID, List.of("a1"), 0);
        String orderId = created.getId();

        // Act — add a second seat from the same zone
        ActiveOrderDTO updated = activeOrderService.addTicketsToOrder(
                orderId, token, VALID_ZONE_ID, List.of("a2"), 0);

        // Assert — order has both tickets; subtotal reflects two seats; both seats locked
        assertEquals(2, updated.getTicketIds().size());
        assertEquals(100.0, updated.getSubtotal(), 0.001);
        Show updatedShow = showRepo.findById(showId);
        assertTrue(updatedShow.getVenueMap().getSeatById(VALID_ZONE_ID, "a1").isTaken());
        assertTrue(updatedShow.getVenueMap().getSeatById(VALID_ZONE_ID, "a2").isTaken());
    }

    @Test
    void givenNoOrderForId_whenAddTickets_thenThrowsAndNoSideEffects() {
        // Arrange — show exists but the order id does not
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        int ticketsBefore = ticketRepo.getAll().size();

        // Act + Assert
        assertThrows(Exception.class, () -> activeOrderService.addTicketsToOrder(
                "no-such-order", token, VALID_ZONE_ID, List.of(VALID_SEAT_ID), 0));

        // Assert — no ticket was created, the seat in the venue map is still available
        assertEquals(ticketsBefore, ticketRepo.getAll().size());
        assertFalse(showRepo.findById(show.getShowId()).getVenueMap()
                .getSeatById(VALID_ZONE_ID, VALID_SEAT_ID).isTaken());
    }

    @Test
    void givenExpiredOrder_whenAddTickets_thenThrowsAndSeatStaysAvailable() throws Exception {
        // Arrange — show with two free seats; stash an expired order for the member
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        Seat seatA1 = Seat.builder().id("a1").row(1).number(1).price(50.0).build();
        Seat seatA2 = Seat.builder().id("a2").row(1).number(2).price(50.0).build();
        Zone zoneA = Zone.builder().zoneId(VALID_ZONE_ID).standing(false).build();
        zoneA.addSeat(seatA1);
        zoneA.addSeat(seatA2);
        Show show = Show.builder().eventId(event.getEventId()).active(true).build();
        showRepo.store(show);
        VenueMap venueMap = VenueMap.builder().showId(show.getShowId()).build();
        venueMap.addZone(zoneA);
        show.setVenueMap(venueMap);
        showRepo.store(show);

        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        Ticket existingTicket = new Ticket("t-expired", show.getShowId(), VALID_ZONE_ID, "a1", 50.0);
        ticketRepo.store(existingTicket);
        ActiveOrder expired = storeActiveOrderWithTicket(
                ACTIVE_ORDER_ID_1, event.getEventId(), show.getShowId(), existingTicket, EXPIRED_LOCK_MINUTES);
        linkActiveOrderToMember(VALID_EMAIL, expired.getId());

        // Act + Assert — adding to an expired order throws and the second seat stays free
        assertThrows(Exception.class, () -> activeOrderService.addTicketsToOrder(
                expired.getId(), token, VALID_ZONE_ID, List.of("a2"), 0));

        assertFalse(showRepo.findById(show.getShowId()).getVenueMap()
                .getSeatById(VALID_ZONE_ID, "a2").isTaken());
    }

    @Test
    void givenOrderOwnedByAnotherMember_whenAddTickets_thenThrowsUnauthorized() throws Exception {
        // Arrange — user1 reserves an order; user2 tries to add to it
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        Seat seatA1 = Seat.builder().id("a1").row(1).number(1).price(50.0).build();
        Seat seatA2 = Seat.builder().id("a2").row(1).number(2).price(50.0).build();
        Zone zoneA = Zone.builder().zoneId(VALID_ZONE_ID).standing(false).build();
        zoneA.addSeat(seatA1);
        zoneA.addSeat(seatA2);
        Show show = Show.builder().eventId(event.getEventId()).active(true).build();
        showRepo.store(show);
        VenueMap venueMap = VenueMap.builder().showId(show.getShowId()).build();
        venueMap.addZone(zoneA);
        show.setVenueMap(venueMap);
        showRepo.store(show);
        String showId = show.getShowId();

        String ownerToken = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        grantQueueAccess(showId, VALID_EMAIL);
        ActiveOrderDTO owned = activeOrderService.reserveTickets(
                showId, ownerToken, null, null, VALID_ZONE_ID, List.of("a1"), 0);

        String otherToken = storeMemberAndLogin(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);

        // Act + Assert — user2 is logged in but the order is linked to user1
        assertThrows(domain.exceptions.UnauthorizedException.class,
                () -> activeOrderService.addTicketsToOrder(
                        owned.getId(), otherToken, VALID_ZONE_ID, List.of("a2"), 0));

        // Assert — order unchanged, second seat still available
        ActiveOrder unchanged = activeOrderRepo.findById(owned.getId());
        assertEquals(1, unchanged.getTicketIds().size());
        assertFalse(showRepo.findById(showId).getVenueMap()
                .getSeatById(VALID_ZONE_ID, "a2").isTaken());
    }

    @Test
    void givenOrderInZoneA_whenAddSeatFromZoneB_thenThrowsAndZoneBSeatStaysAvailable() throws Exception {
        // Arrange — show has zone A (seat a1) and zone B (seat b1); reserve a1
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        Seat seatA = Seat.builder().id("a1").row(1).number(1).price(50.0).build();
        Zone zoneA = Zone.builder().zoneId("zoneA").zoneName("Premium").standing(false).build();
        zoneA.addSeat(seatA);
        Seat seatB = Seat.builder().id("b1").row(1).number(1).price(80.0).build();
        Zone zoneB = Zone.builder().zoneId("zoneB").zoneName("Regular").standing(false).build();
        zoneB.addSeat(seatB);

        Show show = Show.builder().eventId(event.getEventId()).active(true).build();
        showRepo.store(show);
        VenueMap venueMap = VenueMap.builder().showId(show.getShowId()).build();
        venueMap.addZone(zoneA);
        venueMap.addZone(zoneB);
        show.setVenueMap(venueMap);
        showRepo.store(show);
        String showId = show.getShowId();

        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        grantQueueAccess(showId, VALID_EMAIL);
        ActiveOrderDTO inZoneA = activeOrderService.reserveTickets(
                showId, token, null, null, "zoneA", List.of("a1"), 0);

        // Act + Assert — adding a zone B seat is rejected with a message naming zone A
        Exception ex = assertThrows(Exception.class,
                () -> activeOrderService.addTicketsToOrder(
                        inZoneA.getId(), token, "zoneB", List.of("b1"), 0));
        assertTrue(ex.getMessage().contains("Premium"),
                "error message should name the locked zone; got: " + ex.getMessage());

        // Assert — order unchanged, zone B seat NOT locked
        ActiveOrder unchanged = activeOrderRepo.findById(inZoneA.getId());
        assertEquals(1, unchanged.getTicketIds().size());
        assertFalse(showRepo.findById(showId).getVenueMap().getSeatById("zoneB", "b1").isTaken());
    }

    @Test
    void givenOrderEmptiedAfterRemove_whenAddSeatFromDifferentZone_thenSucceeds() throws Exception {
        // Arrange — two-zone show; reserve a1 in zone A
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        Seat seatA = Seat.builder().id("a1").row(1).number(1).price(50.0).build();
        Zone zoneA = Zone.builder().zoneId("zoneA").zoneName("Premium").standing(false).build();
        zoneA.addSeat(seatA);
        Seat seatB = Seat.builder().id("b1").row(1).number(1).price(80.0).build();
        Zone zoneB = Zone.builder().zoneId("zoneB").zoneName("Regular").standing(false).build();
        zoneB.addSeat(seatB);

        Show show = Show.builder().eventId(event.getEventId()).active(true).build();
        showRepo.store(show);
        VenueMap venueMap = VenueMap.builder().showId(show.getShowId()).build();
        venueMap.addZone(zoneA);
        venueMap.addZone(zoneB);
        show.setVenueMap(venueMap);
        showRepo.store(show);
        String showId = show.getShowId();

        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        grantQueueAccess(showId, VALID_EMAIL);
        ActiveOrderDTO inZoneA = activeOrderService.reserveTickets(
                showId, token, null, null, "zoneA", List.of("a1"), 0);
        String ticketAId = inZoneA.getTicketIds().get(0);

        // Act 1 — remove the only ticket so the order is empty
        activeOrderService.removeTicketFromOrder(inZoneA.getId(), ticketAId);
        assertEquals(0, activeOrderRepo.findById(inZoneA.getId()).getTicketIds().size());

        // Act 2 — adding from zone B is now allowed
        ActiveOrderDTO switched = activeOrderService.addTicketsToOrder(
                inZoneA.getId(), token, "zoneB", List.of("b1"), 0);

        // Assert — order now holds the zone B ticket and the zone B seat is locked
        assertEquals(1, switched.getTicketIds().size());
        Show updatedShow = showRepo.findById(showId);
        assertTrue(updatedShow.getVenueMap().getSeatById("zoneB", "b1").isTaken());
        assertFalse(updatedShow.getVenueMap().getSeatById("zoneA", "a1").isTaken());
    }

    ///////// Race Condition Tests /////////
    
    @Test
    void givenMultipleUsersAndOneAvailableSeat_whenConcurrentReserve_thenOnlyOneSucceeds() throws InterruptedException {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        String showId = show.getShowId();

        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            String email = "user" + i + "@mail.com";
            storeMember(email, email, VALID_PASSWORD);
            String token = authService.generateToken(email);
            grantQueueAccess(showId, email);
            
            executor.submit(() -> {
                try {
                    startLatch.await(); // Point of synchronized start
                    activeOrderService.reserveTickets(showId, token, null, null, VALID_ZONE_ID, List.of(VALID_SEAT_ID), 0);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Act
        startLatch.countDown(); // Trigger all threads at once
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertTrue(completed, "Test timed out");
        assertEquals(1, successCount.get(), "Exactly one reservation should succeed");
        assertEquals(numberOfThreads - 1, failureCount.get(), "All other reservations should fail");
        assertEquals(1, activeOrderRepo.getAll().size(), "Only one active order should exist in the repository");

        // Verify the seat is taken
        Show updatedShow = showRepo.findById(showId);
        assertTrue(updatedShow.getVenueMap().getSeatById(VALID_ZONE_ID, VALID_SEAT_ID).isTaken());
    }

    @Test
    void givenMultipleUsersAndLimitedStandingTickets_whenConcurrentReserve_thenOnlyAvailableAmountSucceeds() throws InterruptedException {
        // Arrange
        int initialCapacity = 5;
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithStandingZone(event.getEventId(), VALID_ZONE_ID, initialCapacity);
        String showId = show.getShowId();

        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            String email = "user" + i + "@mail.com";
            storeMember(email, email, VALID_PASSWORD);
            String token = authService.generateToken(email);
            grantQueueAccess(showId, email);
            
            executor.submit(() -> {
                try {
                    startLatch.await();
                    activeOrderService.reserveTickets(showId, token, null, null, VALID_ZONE_ID, null, 1);
                    successCount.incrementAndGet();
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
        assertEquals(initialCapacity, successCount.get(), "Exactly 'initialCapacity' reservations should succeed");
        assertEquals(numberOfThreads - initialCapacity, failureCount.get(), "All other reservations should fail");
        assertEquals(initialCapacity, activeOrderRepo.getAll().size(), "Only 'initialCapacity' active orders should exist in the repository");

        // Verify capacity is 0
        Show updatedShow = showRepo.findById(showId);
        assertEquals(0, updatedShow.getVenueMap().getZoneById(VALID_ZONE_ID).getStandingAvailable());
    }

    ///////// getActiveOrderIdForMember (#209 resume-reservation) /////////

    @Test
    void givenNullToken_whenGetActiveOrderIdForMember_thenReturnsNull() {
        assertNull(activeOrderService.getActiveOrderIdForMember(null));
        assertNull(activeOrderService.getActiveOrderIdForMember(""));
    }

    @Test
    void givenInvalidToken_whenGetActiveOrderIdForMember_thenReturnsNull() {
        assertNull(activeOrderService.getActiveOrderIdForMember("not-a-real-token"));
    }

    @Test
    void givenLoggedInMemberWithNoActiveOrder_whenGetActiveOrderIdForMember_thenReturnsNull() {
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        assertNull(activeOrderService.getActiveOrderIdForMember(token));
    }

    @Test
    void givenLoggedInMemberWithUnexpiredActiveOrder_whenGetActiveOrderIdForMember_thenReturnsOrderId() {
        // Arrange — reserve a real ticket so the member is linked to an unexpired order
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        grantQueueAccess(show.getShowId(), VALID_EMAIL);

        ActiveOrderDTO reserved = assertDoesNotThrow(() ->
                activeOrderService.reserveTickets(show.getShowId(), token, null, null,
                        VALID_ZONE_ID, List.of(VALID_SEAT_ID), 0));

        // Act
        String returnedId = activeOrderService.getActiveOrderIdForMember(token);

        // Assert
        assertEquals(reserved.getId(), returnedId);
    }

    @Test
    void givenLoggedInMemberWithExpiredActiveOrder_whenGetActiveOrderIdForMember_thenReturnsNull() {
        // Arrange — reserve, then force the order's expirationTime into the past
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        grantQueueAccess(show.getShowId(), VALID_EMAIL);

        ActiveOrderDTO reserved = assertDoesNotThrow(() ->
                activeOrderService.reserveTickets(show.getShowId(), token, null, null,
                        VALID_ZONE_ID, List.of(VALID_SEAT_ID), 0));

        ActiveOrder stored = activeOrderRepo.findById(reserved.getId());
        stored.setExpirationTime(LocalDateTime.now().minusMinutes(1));
        activeOrderRepo.store(stored);

        // Act + Assert — expired orders must not surface as resumable
        assertNull(activeOrderService.getActiveOrderIdForMember(token));
    }
}