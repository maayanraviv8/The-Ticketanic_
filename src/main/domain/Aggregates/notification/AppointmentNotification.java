package domain.Aggregates.notification;

import domain.Aggregates.productioncompany.Permissions;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Set;

@Entity
@DiscriminatorValue("APPOINTMENT")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AppointmentNotification extends Notification {

    @Column(name = "appointer_id")
    private String appointerID;

    @ElementCollection(targetClass = Permissions.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "appointment_notification_permissions", joinColumns = @JoinColumn(name = "notification_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission")
    private Set<Permissions> permissions;

    public AppointmentNotification(String recipientEmail, String subject, String content, String id, 
                                 String companyID, String appointerID, Set<Permissions> permissions) {
        super(recipientEmail, subject, content, id, companyID);
        this.appointerID = appointerID;
        this.permissions = permissions;
    }
}
