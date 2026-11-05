package domain.Aggregates.virtualqueue;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggregate Root for managing the virtual queue for a specific show.
 * Handles concurrency limits, waiting lists, and browsing session expirations
 * to prevent overwhelming the reservation system during high traffic.
 */
public class VirtualQueue {

    @Getter
    private String showId;

    @Getter
    private int maxConcurrentUsers;

    private int browsingSessionMinutes;

    private LinkedList<String> waitingUsers;
    private Map<String, LocalDateTime> allowedUsers; // Maps userId to their browsing-session expiration timestamp

    public VirtualQueue(String showId, int maxConcurrentUsers, int browsingSessionMinutes) {
        this.showId = showId;
        this.maxConcurrentUsers = maxConcurrentUsers;
        this.browsingSessionMinutes = browsingSessionMinutes;
        this.waitingUsers = new LinkedList<>();
        this.allowedUsers = new ConcurrentHashMap<>();
    }

    public VirtualQueue(String showId, int maxConcurrentUsers) {
        this(showId, maxConcurrentUsers, 10);
    }

    /**
     * Called when a user attempts to access the inventory or check their status.
     * 1. Removes expired users from allowedUsers.
     * 2. Moves users from waitingUsers to allowedUsers if there is capacity.
     * 3. Calculates the position for the requesting user.
     */
    public synchronized QueueStatus joinQueue(String userId) {
        processQueue();

        if (allowedUsers.containsKey(userId)) {
            return new QueueStatus(0, true);
        }

        if (!waitingUsers.contains(userId)) {
            waitingUsers.addLast(userId);
            // After adding, see if we can immediately process them
            processQueue();
        }

        if (allowedUsers.containsKey(userId)) {
            return new QueueStatus(0, true);
        }

        int position = waitingUsers.indexOf(userId) + 1; // 1-based indexing for position
        return new QueueStatus(position, false);
    }

    public synchronized QueueStatus getStatus(String userId) {
        processQueue();

        if (allowedUsers.containsKey(userId)) {
            return new QueueStatus(0, true);
        }

        if (waitingUsers.contains(userId)) {
            int position = waitingUsers.indexOf(userId) + 1;
            return new QueueStatus(position, false);
        }

        return new QueueStatus(-1, false); // User not in queue
    }

    /**
     * Checks if a user is currently allowed to access the inventory.
     * Removes them if their time has expired.
     */
    public synchronized boolean isAllowed(String userId) {
        processQueue();
        return allowedUsers.containsKey(userId);
    }

    /**
     * Self-enforcing gate: throws if the user does not currently hold an allowed slot.
     * Same exception type the service used to throw inline, so behavior is preserved.
     */
    public synchronized void requireAllowed(String userId) throws Exception {
        if (!isAllowed(userId)) {
            throw new Exception("User is not allowed to reserve tickets. Please join the virtual queue.");
        }
    }

    /**
     * Explicitly removes a user from the allowed session,
     * usually called after a successful order checkout or cancellation.
     */
    public synchronized void completeSession(String userId) {
        allowedUsers.remove(userId);
        waitingUsers.remove(userId);
        processQueue();
    }

    public synchronized void leaveQueue(String userId) {
        waitingUsers.remove(userId);
        allowedUsers.remove(userId);
        processQueue();
    }

    /**
     * Internal method to clean up expired sessions and grant access to waiting users.
     */
    private void processQueue() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Remove expired users
        allowedUsers.entrySet().removeIf(entry -> entry.getValue().isBefore(now));

        // 2. Grant access to waiting users up to the capacity limit
        while (allowedUsers.size() < maxConcurrentUsers && !waitingUsers.isEmpty()) {
            String nextUser = waitingUsers.removeFirst();
            allowedUsers.put(nextUser, now.plusMinutes(browsingSessionMinutes));
        }
    }
}
