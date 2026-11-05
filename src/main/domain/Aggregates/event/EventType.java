package domain.Aggregates.event;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * EventType - Represents event type classifications (Value Object - immutable)
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EventType {
    private String typeName;
    private String description;
}
