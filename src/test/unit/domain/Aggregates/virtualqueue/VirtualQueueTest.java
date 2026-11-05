package unit.domain.Aggregates.virtualqueue;

import domain.Aggregates.virtualqueue.QueueStatus;
import domain.Aggregates.virtualqueue.VirtualQueue;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class VirtualQueueTest {

    @Test
    void givenEmptyQueue_whenJoinQueue_thenAllowedImmediately() {
        // Arrange
        VirtualQueue queue = new VirtualQueue("show1", 2);

        // Act
        QueueStatus status = queue.joinQueue("user1");

        // Assert
        assertTrue(status.isAllowed());
        assertEquals(0, status.getPosition());
        assertTrue(queue.isAllowed("user1"));
    }

    @Test
    void givenFullQueue_whenJoinQueue_thenPlacedInWaitingList() {
        // Arrange
        VirtualQueue queue = new VirtualQueue("show1", 2);
        queue.joinQueue("user1");
        queue.joinQueue("user2");

        // Act
        QueueStatus status = queue.joinQueue("user3");

        // Assert
        assertFalse(status.isAllowed());
        assertEquals(1, status.getPosition());
        assertFalse(queue.isAllowed("user3"));
    }

    @Test
    void givenUserInQueue_whenAllowedUserLeaves_thenWaitingUserIsAllowed() {
        // Arrange
        VirtualQueue queue = new VirtualQueue("show1", 2);
        queue.joinQueue("user1");
        queue.joinQueue("user2");
        queue.joinQueue("user3");

        // Act
        queue.completeSession("user1");
        QueueStatus status = queue.getStatus("user3");

        // Assert
        assertTrue(status.isAllowed());
        assertEquals(0, status.getPosition());
        assertTrue(queue.isAllowed("user3"));
    }

    @Test
    void givenAllowedUsersWithExpiredTimers_whenProcessQueue_thenExpiredUsersRemoved() throws Exception {
        // Arrange
        VirtualQueue queue = new VirtualQueue("show1", 2);
        
        // Use reflection to manually inject an expired timer since the state is encapsulated
        java.lang.reflect.Field allowedUsersField = VirtualQueue.class.getDeclaredField("allowedUsers");
        allowedUsersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, LocalDateTime> allowedUsers = (java.util.Map<String, LocalDateTime>) allowedUsersField.get(queue);
        allowedUsers.put("user1", LocalDateTime.now().minusMinutes(1));
        queue.joinQueue("user2");
        queue.joinQueue("user3");

        // Act - this should trigger processQueue, evicting user1
        QueueStatus status = queue.getStatus("user3");

        // Assert - user3 should now be allowed because user1 expired
        assertTrue(status.isAllowed());
        assertFalse(queue.isAllowed("user1"));
    }
}
