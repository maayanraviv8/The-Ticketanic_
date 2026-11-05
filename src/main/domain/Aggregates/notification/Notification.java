package domain.Aggregates.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "notifications")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "notification_type", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Notification {

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "subject")
    private String subject;

    @Column(name = "content", length = 1000)
    private String content;

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "company_id")
    private String companyID;

    @Version
    private long version = 0L;

    public Notification(String recipientEmail, String subject, String content, String id, String companyID) {
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.content = content;
        this.id = id;
        this.companyID = companyID;
    }
}
