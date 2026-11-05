package domain.Aggregates.productioncompany;

import java.util.EnumSet;
import java.util.Set;
import domain.Aggregates.productioncompany.Permissions;
import jakarta.persistence.*;
import lombok.*;

@Entity
@DiscriminatorValue("MANAGER")
@NoArgsConstructor
@Getter
@Setter
public class ManagerRole  extends RoleState {

    @ElementCollection(targetClass = Permissions.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "manager_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission")
    Set<Permissions> permissions;
    
    public ManagerRole(String memberID, String appointedID, Set<Permissions> permissions) {
        this.memberID = memberID;
        this.appointedID = appointedID;
        this.permissions = permissions.isEmpty()
                ? EnumSet.noneOf(Permissions.class)
                : EnumSet.copyOf(permissions);
    }

    @Override
    public boolean hasPermission(Permissions p) {
        return this.permissions.contains(p);
    }

    @Override
    public String getAppointedID() {
        return this.appointedID;
    }

    public void addPermissions(Set<Permissions> permissions) {
        this.permissions.addAll(permissions);
    }

    public void removePermission(Permissions p) {
        this.permissions.remove(p);
    }

    public void removePermissions(Set<Permissions> permissions) {
        this.permissions.removeAll(permissions);
    }

    public void addPermission(Permissions p) {
        this.permissions.add(p);
    }

    @Override
    public Set<Permissions> getPermissions() {
        return this.permissions;
    }

    @Override
    public String toString() {
        return "Manager: "+memberID+" permissions: "+permissions.toString()+"\n";
    }

    @Override
    public boolean shouldNotifyOnCompanyEvent() {
        return true; // Managers should be notified
    }

    @Override
    public boolean memberIsOwner() {
        return false;
    }

    @Override
    public boolean memberIsManager() {
        return true;
    }
}
