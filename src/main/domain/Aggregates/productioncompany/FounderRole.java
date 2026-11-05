package domain.Aggregates.productioncompany;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;

import java.util.EnumSet;
import java.util.Set;


@Entity
@DiscriminatorValue("FOUNDER")
@NoArgsConstructor
public class FounderRole extends RoleState {    
    public FounderRole(String memberID) {
        this.memberID = memberID;
    }

    @Override
    public boolean hasPermission(Permissions p) {
        return true;
    }

    @Override
    public String getAppointedID() {
        return null;
    }

    @Override
    public Set<Permissions> getPermissions() {
        return EnumSet.allOf(Permissions.class);
    }

    @Override
    public String toString() {
        return "Founder: " + memberID + " have all permissions/n";
    }

    @Override
    public boolean shouldNotifyOnCompanyEvent() {
        return false; // Founders do not get notifications
    }

    @Override
    public boolean memberIsOwner() {
        return false;
    }

    @Override
    public boolean memberIsManager() {
        return false;
    }
}
