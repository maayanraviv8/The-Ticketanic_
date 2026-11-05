package domain.Aggregates.show;

import jakarta.persistence.*;
import lombok.*;

/**
 * Seat - Represents an individual seat in a zone (Value Object - immutable)
 */
@Entity
@Table(name = "seats")
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Seat {
    @Id
    @Column(name = "seat_id")
    private String id;

    @Column(name = "row_number")
    private int row;

    @Column(name = "seat_number")
    private int number;

    @Column(name = "is_taken")
    @Builder.Default
    private boolean isTaken = false;

    private double price;

    public int getSeatNumber() {
        return number;
    }

    public int getRowNumber() {
        return row;
    }
}
