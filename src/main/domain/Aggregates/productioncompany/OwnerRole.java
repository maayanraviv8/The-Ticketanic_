package domain.Aggregates.productioncompany;

import domain.Aggregates.productioncompany.Permissions;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;

import java.util.EnumSet;
import java.util.Set;
@Entity
@DiscriminatorValue("OWNER")
@NoArgsConstructor
public class OwnerRole extends RoleState {

    public OwnerRole(String memberID, String appointedID) {
        this.memberID = memberID;
        this.appointedID = appointedID;
    }

    @Override
    public boolean hasPermission(Permissions p) {
        return true;
    }

    @Override
    public String getAppointedID() {
        return this.appointedID;
    }

    @Override
    public Set<Permissions> getPermissions() {
        return EnumSet.allOf(Permissions.class);
    }

    @Override
    public String toString() {
        return "Owner: "+memberID+" have all permissions/n";
    }

    @Override
    public boolean shouldNotifyOnCompanyEvent() {
        return true; // Owners should be notified
    }

    @Override
    public boolean memberIsOwner() {
        return true;
    }

    @Override
    public boolean memberIsManager() {
        return false;
    }

}
