package application.dto;

import domain.Aggregates.productioncompany.RoleState;
import domain.Aggregates.productioncompany.FounderRole;
import domain.Aggregates.productioncompany.OwnerRole;
import domain.Aggregates.productioncompany.ManagerRole;
import domain.Aggregates.productioncompany.Permissions;

import java.util.Set;

public class RoleDTO {
    public final String memberId;
    public final String roleType;
    public final String appointerId;
    public final Set<Permissions> permissions;

    public RoleDTO(String memberId, String roleType, String appointerId, Set<Permissions> permissions) {
        this.memberId = memberId;
        this.roleType = roleType;
        this.appointerId = appointerId;
        this.permissions = permissions;
    }

    public static RoleDTO fromEntity(RoleState role) {
        if (role == null) return null;

        String roleType = "Member";
        if (role instanceof FounderRole) {
            roleType = "Founder";
        } else if (role instanceof OwnerRole) {
            roleType = "Owner";
        } else if (role instanceof ManagerRole) {
            roleType = "Manager";
        }

        return new RoleDTO(
            role.getMemberID(),
            roleType,
            role.getAppointedID(),
            role.getPermissions()
        );
    }
}
