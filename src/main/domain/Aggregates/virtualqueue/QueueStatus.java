package domain.Aggregates.virtualqueue;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Value Object representing the position and access status of a user in the virtual queue.
 */
@Getter
@AllArgsConstructor
public class QueueStatus {
    private int position; // Also acts as estimated wait time
    private boolean isAllowed;
}
