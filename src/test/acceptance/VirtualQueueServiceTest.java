package acceptance;

import application.dto.QueueStatusDTO;
import application.service.VirtualQueueService;
import domain.Aggregates.event.Event;
import domain.Aggregates.show.Show;
import domain.Aggregates.virtualqueue.VirtualQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VirtualQueueServiceTest extends AcceptanceTestBase {

    private VirtualQueueService queueService;

    @BeforeEach
    void setUp() {
        queueService = new VirtualQueueService(virtualQueueRepo, showRepo, memberRepo, authService);
    }

    @Test
    void givenUser_whenJoinVirtualQueue_thenStatusIsReturned() throws Exception {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        // Act
        QueueStatusDTO status = queueService.joinVirtualQueue(show.getShowId(), token);

        // Assert
        assertNotNull(status);
        assertTrue(status.isAllowed());
        assertEquals(0, status.getPosition());
    }

    @Test
    void givenQueueAtCapacity_whenNewUserJoins_thenPlacedInWaitingListNotAllowed() throws Exception {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);

        // Force capacity = 1 so the second user can't get in
        virtualQueueRepo.store(new VirtualQueue(show.getShowId(), 1));

        String firstToken = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        String secondToken = storeMemberAndLogin(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);

        queueService.joinVirtualQueue(show.getShowId(), firstToken); // fills capacity

        // Act
        QueueStatusDTO second = queueService.joinVirtualQueue(show.getShowId(), secondToken);

        // Assert
        assertFalse(second.isAllowed());
        assertEquals(1, second.getPosition());
    }

    @Test
    void givenWaiterAfterAllowedLeaves_whenCheckStatus_thenPromotedToAllowed() throws Exception {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        virtualQueueRepo.store(new VirtualQueue(show.getShowId(), 1));

        String firstToken = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        String secondToken = storeMemberAndLogin(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);

        queueService.joinVirtualQueue(show.getShowId(), firstToken);
        QueueStatusDTO secondBefore = queueService.joinVirtualQueue(show.getShowId(), secondToken);
        assertFalse(secondBefore.isAllowed());

        // Act — first user leaves, second polls status
        queueService.releaseQueueSession(show.getShowId(), firstToken);
        QueueStatusDTO secondAfter = queueService.checkQueueStatus(show.getShowId(), secondToken);

        // Assert
        assertTrue(secondAfter.isAllowed());
        assertEquals(0, secondAfter.getPosition());
    }

    ///////// Guest path /////////

    @Test
    void givenGuest_whenJoinVirtualQueueAsGuest_thenAllowedImmediately() throws Exception {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);

        // Act
        QueueStatusDTO status = queueService.joinVirtualQueueAsGuest(show.getShowId(), "guest-1");

        // Assert
        assertNotNull(status);
        assertTrue(status.isAllowed());
        assertEquals(0, status.getPosition());
    }

    @Test
    void givenGuestQueueAtCapacity_whenNewGuestJoins_thenPlacedInWaitingListNotAllowed() throws Exception {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        virtualQueueRepo.store(new VirtualQueue(show.getShowId(), 1));

        queueService.joinVirtualQueueAsGuest(show.getShowId(), "guest-A"); // fills capacity

        // Act
        QueueStatusDTO second = queueService.joinVirtualQueueAsGuest(show.getShowId(), "guest-B");

        // Assert
        assertFalse(second.isAllowed());
        assertEquals(1, second.getPosition());
    }

    @Test
    void givenGuestInQueue_whenReleaseAsGuest_thenRemoved() throws Exception {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);

        queueService.joinVirtualQueueAsGuest(show.getShowId(), "guest-1");

        // Act
        queueService.releaseQueueSessionAsGuest(show.getShowId(), "guest-1");
        QueueStatusDTO status = queueService.checkQueueStatusAsGuest(show.getShowId(), "guest-1");

        // Assert
        assertFalse(status.isAllowed());
        assertEquals(-1, status.getPosition());
    }

    @Test
    void givenNullGuestId_whenJoinAsGuest_thenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> queueService.joinVirtualQueueAsGuest("any-show", null));
    }

    @Test
    void givenUserInQueue_whenLeaveVirtualQueue_thenUserIsRemoved() throws Exception {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        queueService.joinVirtualQueue(show.getShowId(), token);

        // Act
        queueService.releaseQueueSession(show.getShowId(), token);
        QueueStatusDTO status = queueService.checkQueueStatus(show.getShowId(), token);

        // Assert
        assertFalse(status.isAllowed());
        assertEquals(-1, status.getPosition()); // -1 indicates not in queue
    }
}
