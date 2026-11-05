package domain.Aggregates.event;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A unique, time-limited authorization granted to a raffle winner (requirement II.3.9).
 * The code is what lets the winner enter the reservation phase, and only until it expires.
 */
@Entity
@Table(name = "raffle_authorizations")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RaffleAuthorization {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "winner_email", nullable = false)
    private String winnerEmail;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raffle_id", nullable = false)
    private Raffle raffle;

    public RaffleAuthorization(String code, String winnerEmail, LocalDateTime expiresAt) {
        this.code = code;
        this.winnerEmail = winnerEmail;
        this.expiresAt = expiresAt;
    }

    public boolean isValid(LocalDateTime now) {
        return expiresAt != null && now.isBefore(expiresAt);
    }
}
