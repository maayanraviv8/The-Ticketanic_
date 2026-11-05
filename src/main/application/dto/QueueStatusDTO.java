package application.dto;

import domain.Aggregates.virtualqueue.QueueStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Data Transfer Object representing the user's status in the virtual queue.
 * Used to communicate queue position and access permission to the frontend/client.
 */
@Getter
@AllArgsConstructor
public class QueueStatusDTO {
    private int position; // Also acts as estimated wait time
    private boolean isAllowed;

    /**
     * Factory method to create a DTO from a domain aggregate's status.
     *
     * @param status The QueueStatus domain object.
     * @return A mapped QueueStatusDTO instance.
     */
    public static QueueStatusDTO fromEntity(QueueStatus status) {
        if (status == null) return null;
        return new QueueStatusDTO(status.getPosition(), status.isAllowed());
    }
}
