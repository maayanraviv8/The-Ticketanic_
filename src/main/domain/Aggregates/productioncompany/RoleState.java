package domain.Aggregates.productioncompany;

import domain.Aggregates.productioncompany.Permissions;
import jakarta.persistence.*;
import lombok.*;


import java.util.Set;
@Entity
@Table(name = "production_company_roles")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "role_type", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
public abstract class RoleState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    protected String memberID;

    @Column(name = "appointed_id")
    protected String appointedID;

    abstract public boolean hasPermission(Permissions p);
    abstract public String getAppointedID();
    abstract public Set<Permissions> getPermissions();
    abstract public String toString();
    abstract boolean memberIsOwner();
    abstract boolean memberIsManager();

    /**
     * Determines if this role should be notified of company events (suspension/reopen).
     * Only Owners and Managers receive notifications; Founders do not.
     * @return true if role should be notified, false otherwise
     */
    abstract public boolean shouldNotifyOnCompanyEvent();

    public String getMemberID() {
        return memberID;
    }
}
