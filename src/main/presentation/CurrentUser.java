package presentation;

import application.dto.LoginContextDTO;
import application.dto.MemberDTO;
import application.dto.RoleDTO;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import domain.Aggregates.productioncompany.Permissions;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Component
@VaadinSessionScope
public class CurrentUser {

    // Session-scoped snapshot of the signed-in user: member identity + auth token from
    // login, plus role/admin/suspension flags hydrated by the presenter after login.
    // Permissions are expressed with the domain Permissions enum by design — it is a
    // stable value vocabulary shared across layers (also carried on RoleDTO), not an
    // aggregate, so referencing it here does not breach aggregate-root encapsulation.
    private MemberDTO member;
    private String token;
    // Stable identifier for unauthenticated visitors so they can participate in the virtual queue.
    // Persists for the lifetime of the Vaadin session (one per browser session).
    private final String guestSessionId = UUID.randomUUID().toString();
    private Map<String, RoleDTO> rolesByCompany = Collections.emptyMap();
    private boolean admin;
    private boolean suspended;
    // Set after login (or after a successful in-session reservation) so the UI can
    // surface a "resume reservation" banner. Null when there's no resumable order.
    private String activeOrderId;

    // Logged in iff a member is bound to the session; login state is session-scoped,
    // not a property of the MemberDTO.
    public boolean isLoggedIn() {
        return member != null;
    }

    public String getEmail() {
        return member != null ? member.getEmail() : null;
    }

    public String getUsername() {
        return member != null ? member.getUsername() : null;
    }

    public void setRolesByCompany(Map<String, RoleDTO> rolesByCompany) {
        this.rolesByCompany = rolesByCompany != null ? rolesByCompany : Collections.emptyMap();
    }

    public Set<String> getCompaniesWithRoles() {
        return rolesByCompany.keySet();
    }

    public boolean hasAnyRoleAt(String companyId) {
        return rolesByCompany.containsKey(companyId);
    }

    public boolean hasCompanyPermission(String companyId, Permissions p) {
        RoleDTO role = rolesByCompany.get(companyId);
        return role != null && role.permissions != null && role.permissions.contains(p);
    }

    public void login(LoginContextDTO context) {
        this.member = context.getMember();
        this.token = context.getToken();
        // Roles and admin status are populated by the presenter (RoleService/AdminService)
        // right after login; start from a clean slate here.
        this.rolesByCompany = Collections.emptyMap();
        this.admin = false;
        this.suspended = false;
    }

    public void logout() {
        this.member = null;
        this.token = null;
        this.rolesByCompany = Collections.emptyMap();
        this.admin = false;
        this.suspended = false;
        this.activeOrderId = null;
    }

}
