package domain.Aggregates.productioncompany;

import java.util.*;

import domain.Aggregates.order.CustomerDetails;
import domain.policies.discount.IDiscountRule;
import domain.policies.discount.rules.SimplePercentageDiscountRule;
import domain.policies.purchase.IPurchaseRule;
import domain.policies.purchase.composite.AndPurchasePolicy;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "production_companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ProductionCompany {

    @Id
    @Column(name = "company_name")
    private String name;

    @Column(name = "founder_id", nullable = false)
    private String founderID;


    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinTable(
            name = "production_company_to_roles",
            joinColumns = @JoinColumn(name = "production_company_name"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @MapKey(name = "memberID")
    private Map<String, RoleState> roles;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "production_company_pending_appointees", joinColumns = @JoinColumn(name = "production_company_name"))
    @Column(name = "appointee_id")
    private Set<String> pendingAppointees = new HashSet<>();

    @Column(name = "is_active")
    private boolean isActive;

    @Version
    private long version;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "discount_policy_id", referencedColumnName = "id")
    private IDiscountRule discountPolicy;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "purchase_policy_id", referencedColumnName = "id")
    private IPurchaseRule purchasePolicy;


    public ProductionCompany(String name, String founderID) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Production company name cannot be null or empty");
        }
        if (founderID == null || founderID.trim().isEmpty()) {
            throw new IllegalArgumentException("Founder ID cannot be null or empty");
        }
        this.name = name;
        this.founderID = founderID;
        this.roles = new HashMap<String, RoleState>();
        this.roles.put(founderID, new FounderRole(founderID));
        this.discountPolicy = new SimplePercentageDiscountRule(0.0);
        this.purchasePolicy =  new AndPurchasePolicy(new ArrayList<>());
        this.isActive = true;
        this.version = 0;
    }

    public ProductionCompany(ProductionCompany productionCompany) {
        this.name = productionCompany.name;
        this.founderID = productionCompany.founderID;
        this.discountPolicy = productionCompany.discountPolicy;
        //this.orderPolicy = productionCompany.orderPolicy;
        this.purchasePolicy = productionCompany.purchasePolicy;
        this.roles = new HashMap<>(productionCompany.roles);
        this.isActive = productionCompany.isActive;
        this.version = productionCompany.version;
        this.pendingAppointees = new HashSet<>(productionCompany.pendingAppointees);
    }

    public boolean isPendingAppointee(String appointeeID) {
        return pendingAppointees.contains(appointeeID);
    }

    public void addPendingAppointee(String appointeeID) {
        pendingAppointees.add(appointeeID);
    }

    public void acceptAppointment(String appointerID, String newOwnerID, boolean approved) {
        if(approved) {
            appointOwner(appointerID, newOwnerID);
        }
        pendingAppointees.remove(newOwnerID);
    }

    public void acceptAppointment(String appointerID, String newOwnerID, Set<Permissions> permissions, boolean approved) {
        if(approved) {
            appointManager(appointerID, newOwnerID, permissions);
        }
        pendingAppointees.remove(newOwnerID);
    }

    public void appointOwner(String appointerID, String newOwnerID) {
        if (!roles.containsKey(appointerID) || !roles.get(appointerID).hasPermission(Permissions.APPOINT_OWNERS))
            throw new IllegalArgumentException("Appointer does not have permission to appoint owners");
        if (roles.containsKey(newOwnerID))
            throw new IllegalArgumentException("Member already has a role in this company");
        roles.put(newOwnerID, new OwnerRole(newOwnerID, appointerID));
    }

    public void appointManager(String appointerID, String newManagerID, Set<Permissions> permissions) {
        if (!roles.containsKey(appointerID) || !roles.get(appointerID).hasPermission(Permissions.APPOINT_MANAGERS))
            throw new IllegalArgumentException("Appointer does not have permission to appoint managers");
        if (roles.containsKey(newManagerID))
            throw new IllegalArgumentException("Member already has a role in this company");
        roles.put(newManagerID, new ManagerRole(newManagerID, appointerID, permissions));
    }

    public Set<Permissions> getPermissions(String memberID) {
        return roles.get(memberID).getPermissions();
    }

    public boolean memberHasPermission(String memberID, Permissions permission) {
        return roles.containsKey(memberID) && roles.get(memberID).hasPermission(permission);
    }

    public boolean memberIsOwner(String memberID) {
        return roles.containsKey(memberID) && roles.get(memberID).memberIsOwner();
    }

    public boolean memberIsFounder(String memberID) {
        return memberID.equals(founderID);
    }

    public void removeAppointment(String appointerID, String removeID) {
        if (!roles.containsKey(appointerID)) {
            throw new IllegalArgumentException("Appointer does not have permission to remove member");
        }
        if (!roles.containsKey(removeID)) {
            throw new IllegalArgumentException("Member does not have a role in this company");
        }
        if (!roles.get(removeID).getAppointedID().equals(appointerID)) {
            throw new IllegalArgumentException("Appointer does not appointed the member");
        }
        roles.remove(removeID);
    }

    public void relinquishOwnership(String memberID) {
        if (!roles.containsKey(memberID)) {
            throw new IllegalArgumentException("Member does not have a role in this company");
        }
        if (!(roles.get(memberID).memberIsOwner())) {
            throw new IllegalArgumentException("Member is not an owner");
        }
        roles.remove(memberID);
    }

    public List<String> revokeAllOwnersAndManagers() {
        List<String> affectedMembers = new ArrayList<>();
        Iterator<Map.Entry<String, RoleState>> it = roles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, RoleState> entry = it.next();
            if (!(entry.getKey().equals(founderID))) {
                affectedMembers.add(entry.getKey());
                it.remove();
            }
        }
        return affectedMembers;
    }


    public boolean removeMember(String memberID) {
        return roles.remove(memberID) != null;
    }

    public Map<String, List<String>> getAllRoles(String member) {
        if (!roles.containsKey(member) || !roles.get(member).hasPermission(Permissions.VIEW_MANAGEMENT_HIERARCHY)) {
            throw new IllegalArgumentException("Member does not have a permission to view management hierarchy");
        }
        Map<String, List<String>> hierarchy = new HashMap<>();
        hierarchy.put("Founder", List.of(roles.get(founderID).toString()));
        for (Map.Entry<String, RoleState> entry : roles.entrySet()) {
            String memberID = entry.getKey();
            RoleState role = entry.getValue();
            if (memberID.equals(founderID)) {
                hierarchy.put(founderID, new ArrayList<>());
                continue;
            }
            hierarchy.computeIfAbsent(role.getAppointedID(), k -> new ArrayList<>())
                    .add(memberID);
        }
        return hierarchy;
    }

    public void addManagerPermission(String memberID, String appointedID, Set<Permissions> permissions) {
        if (!roles.containsKey(memberID)){
            throw new IllegalArgumentException("Member does not have a role in this company");
        }
        if (!roles.containsKey(appointedID)){
            throw new IllegalArgumentException("Member does not have a role in this company");
        }
        if(!(roles.get(memberID).memberIsManager())){
            throw new IllegalArgumentException("Member is not a manager, permissions cannot be changed");
        }
        if (!(roles.get(memberID).getAppointedID().equals(appointedID))){
            throw new IllegalArgumentException(appointedID+" did not appoint "+memberID+" cannot change permissions");
        }
        changePermissionsChecking(memberID, appointedID);
        RoleState role = roles.get(memberID);
        ((ManagerRole) role).addPermissions(permissions);
    }

    public void removeManagerPermissions(String memberID, String appointedID, Set<Permissions> permissions) {
        if (!roles.containsKey(memberID)){
            throw new IllegalArgumentException("Member does not have a role in this company");
        }
        if (!roles.containsKey(appointedID)){
            throw new IllegalArgumentException("Member does not have a role in this company");
        }
        if(!(roles.get(memberID).memberIsManager())){
            throw new IllegalArgumentException("Member is not a manager, permissions cannot be changed");
        }
        if (!(roles.get(memberID).getAppointedID().equals(appointedID))){
            throw new IllegalArgumentException(appointedID+" did not appoint "+memberID+" cannot change permissions");
        }
        changePermissionsChecking(memberID, appointedID);
        RoleState role = roles.get(memberID);
        ((ManagerRole) role).removePermissions(permissions);
    }

    private void changePermissionsChecking(String memberID, String appointedID) {
        if (!roles.containsKey(memberID)) {
            throw new IllegalArgumentException("Member does not have a role in this company");
        }
        RoleState role = roles.get(memberID);
        if (!role.getAppointedID().equals(appointedID)) {
            throw new IllegalArgumentException("Appointer did not appoint the member");
        }
        if (!(role.memberIsManager())) {
            throw new IllegalArgumentException("Member is not a manager, permissions cannot be changed");
        }
    }

    public boolean memberHasPermission(String memberID){
        return roles.containsKey(memberID);
    }

    public List<RoleState> getRolesByList() {
        return new ArrayList<>(roles.values());
    }

    public void setInactive() {
        this.isActive = false;
    }

    public void setActive() {
        this.isActive = true;
    }

    public void reopen(String requestingUserId) {
        if (!memberIsFounder(requestingUserId)) {
            throw new IllegalArgumentException("Unauthorized: Only the Founder can reopen the production company.");
        }
        if (isActive()) {
            throw new IllegalArgumentException("Production company is already active.");
        }
        setActive();
    }

    public void suspend(String requestingUserId) {
        if (!memberIsFounder(requestingUserId)) {
            throw new IllegalArgumentException("Unauthorized: Only the Founder can suspend/close the production company.");
        }
        if (!isActive()) {
            throw new IllegalArgumentException("Production company is already inactive.");
        }
        setInactive();
    }

    public List<String> getMembersToNotifyOnSuspension() {
        List<String> membersToNotify = new ArrayList<>();
        for (RoleState role : roles.values()) {
            if (role.shouldNotifyOnCompanyEvent()) {
                membersToNotify.add(role.getMemberID());
            }
        }
        return membersToNotify;
    }

    public List<String> getMembersToNotifyOnReopen() {
        List<String> membersToNotify = new ArrayList<>();
        for (RoleState role : roles.values()) {
            if (role.shouldNotifyOnCompanyEvent()) {
                membersToNotify.add(role.getMemberID());
            }
        }
        return membersToNotify;
    }

    public List<String> getManagerSubtree(String rootManagerId) {
        List<String> subtree = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(rootManagerId);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!subtree.contains(current)) {
                subtree.add(current);

                for (Map.Entry<String, RoleState> entry : roles.entrySet()) {
                    String potentialUnderlingId = entry.getKey();
                    RoleState role = entry.getValue();

                    if (current.equals(role.getAppointedID()) && !subtree.contains(potentialUnderlingId)) {
                        queue.add(potentialUnderlingId);
                    }
                }
            }
        }
        return subtree;
    }

    public RoleState getMemberRole(String memberID) {
        return roles.getOrDefault(memberID, null);
    }

    public void isPurchaseAllowed(CustomerDetails customerDetails) throws Exception {
        if (this.purchasePolicy != null) {
            this.purchasePolicy.evaluate(customerDetails);
        }

    }

    public double evaluateDiscount(CustomerDetails customerDetails) {
        if (this.discountPolicy == null) {
            return 0.0;
        }
        return this.discountPolicy.evaluate(customerDetails);
    }
}
