package domain.Aggregates.member;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "suspensions")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Suspension {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "suspension_date", nullable = false)
    private LocalDateTime suspensionDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "member_email", nullable = false)
    private String memberEmail;

    public Suspension(String memberEmail, LocalDateTime suspensionDate) {
        this.memberEmail = memberEmail;
        this.suspensionDate = suspensionDate;
    }

    public void close(LocalDateTime endDate) {
        if (this.endDate != null) {
            throw new IllegalStateException("Suspension is already closed");
        }
        this.endDate = endDate;
    }

    public boolean isActive() {
        return endDate == null;
    }
}