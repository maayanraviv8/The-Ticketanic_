package unit.external.notifier;

import domain.Aggregates.notification.Notification;
import infra.notifier.Broadcaster;
import infra.notifier.Registration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BroadcasterTest {

    private static Notification notif(String subject) {
        return Notification.builder()
                .id(UUID.randomUUID().toString())
                .recipientEmail("test@mail.com")
                .subject(subject)
                .content("content")
                .build();
    }

    @BeforeEach
    void setUp() {
        Broadcaster.reset();
    }

    @Test
    void GivenRegisteredListener_WhenBroadcast_ThenListenerReceivesMessage() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> received = new ArrayList<>();

        Broadcaster.register("alice", n -> {
            received.add(n.getSubject());
            latch.countDown();
        });

        Notification n = notif("hello");
        boolean result = Broadcaster.broadcast("alice", n);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(result);
        assertEquals(List.of("hello"), received);
    }

    @Test
    void GivenNoListener_WhenBroadcast_ThenReturnsFalse() {
        boolean result = Broadcaster.broadcast("bob", notif("hello"));
        assertFalse(result);
    }

    @Test
    void GivenRegistrationRemoved_WhenBroadcast_ThenReturnsFalse() {
        List<String> received = new ArrayList<>();
        Registration reg = Broadcaster.register("alice", n -> received.add(n.getSubject()));

        reg.remove();

        boolean result = Broadcaster.broadcast("alice", notif("hello"));
        assertFalse(result);
        assertTrue(received.isEmpty());
    }

    @Test
    void GivenMultipleListeners_WhenBroadcast_ThenAllReceiveMessage() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        List<String> received = Collections.synchronizedList(new ArrayList<>());

        Broadcaster.register("alice", n -> { received.add("L1:" + n.getSubject()); latch.countDown(); });
        Broadcaster.register("alice", n -> { received.add("L2:" + n.getSubject()); latch.countDown(); });

        boolean result = Broadcaster.broadcast("alice", notif("ping"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(result);
        assertEquals(2, received.size());
        assertTrue(received.contains("L1:ping"));
        assertTrue(received.contains("L2:ping"));
    }

    @Test
    void GivenRegisteredListener_WhenBroadcastToDifferentUser_ThenListenerNotInvoked() {
        List<String> received = new ArrayList<>();
        Broadcaster.register("alice", n -> received.add(n.getSubject()));

        boolean result = Broadcaster.broadcast("bob", notif("hello"));

        assertFalse(result);
        assertTrue(received.isEmpty());
    }
}
