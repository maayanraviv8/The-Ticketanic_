package infra.notifier;

import domain.Aggregates.notification.Notification;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Broadcaster {
    private static final Logger logger = LoggerFactory.getLogger(Broadcaster.class);
    private static final Executor executor = Executors.newCachedThreadPool();
    private static final ConcurrentHashMap<String, ConcurrentLinkedQueue<Consumer<Notification>>> listeners =
            new ConcurrentHashMap<>();

    public static Registration register(String userId, Consumer<Notification> listener) {
        listeners.computeIfAbsent(userId, k -> new ConcurrentLinkedQueue<>()).add(listener);
        return () -> removeListener(userId, listener);
    }

    private static void removeListener(String userId, Consumer<Notification> listener) {
        ConcurrentLinkedQueue<Consumer<Notification>> userListeners = listeners.get(userId);
        if (userListeners != null) {
            userListeners.remove(listener);
            if (userListeners.isEmpty()) {
                listeners.remove(userId);
            }
        }
    }

    public static boolean broadcast(String userId, Notification notification) {
        ConcurrentLinkedQueue<Consumer<Notification>> userListeners = listeners.get(userId);
        if (userListeners == null || userListeners.isEmpty()) {
            return false;
        }

        for (Consumer<Notification> consumer : userListeners) {
            executor.execute(() -> {
                try {
                    consumer.accept(notification);
                } catch (Exception e) {
                    logger.error("Error delivering broadcast to user '{}': {}", userId, e.getMessage());
                }
            });
        }
        return true;
    }

    public static void reset() {
        listeners.clear();
    }
}
