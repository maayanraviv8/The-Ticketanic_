package domain.Aggregates.member;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import domain.exceptions.AccountSuspendedException;
import domain.exceptions.MemberException;
import domain.exceptions.MemberSuspendedException;
import lombok.Getter;
import lombok.Setter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Entity
@Table(name = "members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Member {
    @Id
    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "hashed_password", nullable = false)
    private String hashedPassword;
    
    @Column(name = "active_order_id")
    @Setter(AccessLevel.NONE)
    private String activeOrderId;

    @Column(name = "is_suspended", nullable = false)
    @Setter(AccessLevel.NONE)
    private boolean isSuspended = false;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "member_email", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private List<Suspension> suspensionHistory = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "member_order_ids", joinColumns = @JoinColumn(name = "member_email"))
    @Column(name = "order_id")
    private List<String> orderIds = new ArrayList<>();

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Version
    @Column(name = "version")
    private long version;

    public Member(String username, String email, String hashedPassword) {
        this.username = username;
        this.email = email;
        this.hashedPassword = hashedPassword;
        this.version = 0;
    }


    /**
     * Self-enforcing precondition for member-only flows that must reject suspended accounts
     * (e.g. starting a reservation). Matches the {@link #ensureCanLogin()} pattern of having
     * the aggregate guard its own active-account invariant.
     */
    public void ensureNotSuspended() {
        if (isSuspended)
            throw new MemberSuspendedException(
                "Your account has been suspended. Ticket reservation is not allowed.");
    }

    public boolean hasActiveOrder() {
        return activeOrderId != null;
    }

    public List<Suspension> getSuspensionHistory() { return List.copyOf(suspensionHistory); }

    public List<Suspension> getFilteredSuspensions(boolean activeOnly) {
        if (!activeOnly) return List.copyOf(suspensionHistory);
        return suspensionHistory.stream()
                .filter(Suspension::isActive)
                .collect(Collectors.toUnmodifiableList());
    }

    public void suspend() {
        if (isSuspended) {
            throw new MemberException("Member is already suspended");
        }
        isSuspended = true;
        suspensionHistory.add(new Suspension(this.email, LocalDateTime.now()));
    }

    public void unsuspend() {
        if (!isSuspended) {
            throw new MemberException("Member is not suspended");
        }
        isSuspended = false;
        suspensionHistory.stream()
            .filter(Suspension::isActive)
            .findFirst()
            .ifPresent(s -> s.close(LocalDateTime.now()));
    }

    public void completeActiveOrder(String finalOrderId) {
        if (this.orderIds == null) {
            this.orderIds = new ArrayList<>();
        }
        this.orderIds.add(finalOrderId);
        this.activeOrderId = null;
    }

    public void expireActiveOrder() {
        this.activeOrderId = null;
    }

    public void assignActiveOrder(String activeOrderId) {
        this.activeOrderId = activeOrderId;
    }

    /**
     * Validates that this member is allowed to start a session. Login/logout state
     * is session-scoped (JWT + Vaadin session), not persisted on the aggregate, so
     * this method only enforces the active-account invariant and does not mutate.
     * Password verification stays in the service layer because it needs the
     * password encoder port (application-layer infrastructure).
     *
     * @throws AccountSuspendedException if the account has been cancelled or suspended
     */
    public void ensureCanLogin() {
        if (!isActive) {
            throw new AccountSuspendedException("This account has been cancelled");
        }
        if (isSuspended) {
            throw new AccountSuspendedException("This account has been suspended");
        }
    }

    public void cancelMembership(){
        if (!isActive) {
            throw new MemberException("Member is already canceled");
        }
        this.isActive = false;
    }

    /**
     * Changes this member's display name. Uniqueness across members is a
     * cross-aggregate concern enforced by the service before calling this.
     */
    public void rename(String newUsername) {
        this.username = newUsername;
    }

    /**
     * Replaces this member's stored password hash. The aggregate never sees the
     * raw password: hashing and "same as current" comparison use the password
     * encoder port and stay in the service layer.
     */
    public void changePassword(String newHashedPassword) {
        this.hashedPassword = newHashedPassword;
    }
}
