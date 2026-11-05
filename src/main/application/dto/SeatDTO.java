package application.dto;

import domain.Aggregates.show.Seat;

public class SeatDTO {
    public final String seatId;
    public final int seatNumber;
    public final int rowNumber;
    public final boolean isTaken;

    public SeatDTO(String seatId, int seatNumber, int rowNumber, boolean isTaken) {
        this.seatId = seatId;
        this.seatNumber = seatNumber;
        this.rowNumber = rowNumber;
        this.isTaken = isTaken;
    }

    public static SeatDTO fromEntity(Seat seat) {
        if (seat == null)
            return null;
        return new SeatDTO(seat.getId(), seat.getSeatNumber(), seat.getRowNumber(), seat.isTaken());
    }
}
