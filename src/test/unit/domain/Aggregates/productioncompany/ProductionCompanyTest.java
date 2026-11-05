package unit.domain.Aggregates.productioncompany;

import domain.Aggregates.productioncompany.Permissions;
import domain.Aggregates.productioncompany.ProductionCompany;
import domain.Aggregates.productioncompany.FounderRole;
import domain.Aggregates.productioncompany.RoleState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProductionCompanyTest {

    private ProductionCompany company;
    private static final String FOUNDER = "founder1@mail.com";

    @BeforeEach
    void setUp() {
        company = new ProductionCompany("Acme Shows", FOUNDER);
    }

    @Test
    void givenValidDetails_whenCreated_thenNameAndFounderIdAreSet() {
        ProductionCompany company = new ProductionCompany("TestCompany", "founder@mail.com");

        assertEquals("TestCompany", company.getName(), "Company name should be set correctly");
        assertEquals("founder@mail.com", company.getFounderID(), "Founder ID should be set correctly");
    }

    @Test
    void givenNewCompany_whenCreated_thenIsActiveIsTrue() {
        ProductionCompany company = new ProductionCompany("TestCompany", "founder@mail.com");

        assertTrue(company.isActive(), "A new company should be active by default");
    }

    @Test
    void givenNewCompany_whenCreated_thenFounderRoleIsAssigned() {
        ProductionCompany company = new ProductionCompany("TestCompany", "founder@mail.com");

        List<RoleState> roles = company.getRolesByList();
        assertEquals(1, roles.size(), "There should be exactly 1 role assigned (the founder)");

        RoleState founderRole = roles.get(0);
        assertTrue(founderRole instanceof FounderRole, "The role should be of type FounderRole");
        assertEquals("founder@mail.com", founderRole.getMemberID(), "Founder role should belong to the founder");
    }

    @Test
    void givenNullName_whenCreated_thenThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProductionCompany(null, "founder@mail.com"),
                "Should throw exception when name is null");
    }

    @Test
    void givenEmptyName_whenCreated_thenThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProductionCompany("   ", "founder@mail.com"),
                "Should throw exception when name is empty");
    }

    @Test
    void givenNullFounderId_whenCreated_thenThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProductionCompany("TestCompany", null),
                "Should throw exception when founderId is null");
    }

    @Test
    void givenEmptyFounderId_whenCreated_thenThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProductionCompany("TestCompany", ""),
                "Should throw exception when founderId is empty");
    }

    // ── appointOwner ──────────────────────────────────────────────────────────

    @Test
    void givenFounderAppoints_whenAppointOwner_thenRolesSizeIncreases() {
        int sizeBefore = company.getRolesByList().size();
        company.appointOwner(FOUNDER, "owner@mail.com");
        assertEquals(sizeBefore + 1, company.getRolesByList().size());
    }

    @Test
    void givenFounderAppoints_whenAppointOwner_thenNewMemberIsOwner() {
        company.appointOwner(FOUNDER, "owner@mail.com");
        assertTrue(company.memberIsOwner("owner@mail.com"));
    }

    @Test
    void givenOwnerAppoints_whenAppointOwner_thenNewMemberIsOwner() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        company.appointOwner("owner1@mail.com", "owner2@mail.com");
        assertTrue(company.memberIsOwner("owner2@mail.com"));
    }

    @Test
    void givenMemberAlreadyHasRole_whenAppointOwner_thenThrowsAndSizeUnchanged() {
        company.appointOwner(FOUNDER, "owner@mail.com");
        int sizeBefore = company.getRolesByList().size();

        assertThrows(IllegalArgumentException.class,
                () -> company.appointOwner(FOUNDER, "owner@mail.com"));
        assertEquals(sizeBefore, company.getRolesByList().size());
    }

    @Test
    void givenAppointerNotInCompany_whenAppointOwner_thenThrows() {
        int sizeBefore = company.getRolesByList().size();

        assertThrows(IllegalArgumentException.class,
                () -> company.appointOwner("stranger@mail.com", "owner@mail.com"));
        assertEquals(sizeBefore, company.getRolesByList().size());
    }

    // ── appointManager ────────────────────────────────────────────────────────

    @Test
    void givenFounderAppoints_whenAppointManager_thenRolesSizeIncreases() {
        int sizeBefore = company.getRolesByList().size();
        company.appointManager(FOUNDER, "manager@mail.com", Set.of(Permissions.APPOINT_MANAGERS));
        assertEquals(sizeBefore + 1, company.getRolesByList().size());
    }

    @Test
    void givenFounderAppoints_whenAppointManager_thenManagerHasGivenPermissions() {
        company.appointManager(FOUNDER, "manager@mail.com", Set.of(Permissions.APPOINT_MANAGERS));
        assertTrue(company.memberHasPermission("manager@mail.com", Permissions.APPOINT_MANAGERS));
    }

    @Test
    void givenMemberAlreadyHasRole_whenAppointManager_thenThrowsAndSizeUnchanged() {
        company.appointManager(FOUNDER, "manager@mail.com", Set.of());
        int sizeBefore = company.getRolesByList().size();

        assertThrows(IllegalArgumentException.class,
                () -> company.appointManager(FOUNDER, "manager@mail.com", Set.of()));
        assertEquals(sizeBefore, company.getRolesByList().size());
    }

    @Test
    void givenAppointerNotInCompany_whenAppointManager_thenThrows() {
        int sizeBefore = company.getRolesByList().size();

        assertThrows(IllegalArgumentException.class,
                () -> company.appointManager("stranger@mail.com", "manager@mail.com", Set.of()));
        assertEquals(sizeBefore, company.getRolesByList().size());
    }


    // ── removeAppointment ─────────────────────────────────────────────────────

    @Test
    void givenFounderAppointedOwner_whenRemoveAppointment_thenOwnerRoleRemoved() {
        company.appointOwner(FOUNDER, "owner@mail.com");
        int sizeBeforeRemoval = company.getRoles().size();

        company.removeAppointment(FOUNDER, "owner@mail.com");

        assertFalse(company.memberHasPermission("owner@mail.com"));
        assertEquals(sizeBeforeRemoval - 1, company.getRoles().size());
    }

    @Test
    void givenOwnerAppointedManager_whenRemoveAppointment_thenManagerRoleRemoved() {
        company.appointOwner(FOUNDER, "owner@mail.com");
        company.appointManager("owner@mail.com", "manager@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS));
        int sizeBeforeRemoval = company.getRoles().size();

        company.removeAppointment("owner@mail.com", "manager@mail.com");

        assertFalse(company.memberHasPermission("manager@mail.com"));
        assertEquals(sizeBeforeRemoval - 1, company.getRoles().size());
    }

    @Test
    void givenAppointerNotInRoles_whenRemoveAppointment_thenThrowsException() {
        company.appointOwner(FOUNDER, "owner@mail.com");
        int sizeBeforeRemoval = company.getRoles().size();

        assertThrows(IllegalArgumentException.class, () ->
                company.removeAppointment("nobody@mail.com", "owner@mail.com"));
        assertEquals(sizeBeforeRemoval, company.getRoles().size());
    }

    @Test
    void givenRemoveIDNotInRoles_whenRemoveAppointment_thenThrowsException() {
        int sizeBeforeRemoval = company.getRoles().size();

        assertThrows(IllegalArgumentException.class, () ->
                company.removeAppointment(FOUNDER, "nobody@mail.com"));
        assertEquals(sizeBeforeRemoval, company.getRoles().size());
    }

    @Test
    void givenDifferentOwnerTriesToRemove_whenRemoveAppointment_thenThrowsException() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        company.appointOwner(FOUNDER, "owner2@mail.com");
        int sizeBeforeRemoval = company.getRoles().size();

        // owner2 tries to remove owner1, but owner1 was appointed by FOUNDER
        assertThrows(IllegalArgumentException.class, () ->
                company.removeAppointment("owner2@mail.com", "owner1@mail.com"));
        assertEquals(sizeBeforeRemoval, company.getRoles().size());
    }

    // ── relinquishOwnership ───────────────────────────────────────────────────

    @Test
    void givenOwnerWithFounder_whenRelinquishOwnership_thenOwnerRoleRemoved() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        int sizeBeforeRemoval = company.getRoles().size();

        company.relinquishOwnership("owner1@mail.com");

        assertFalse(company.memberHasPermission("owner1@mail.com"));
        assertEquals(sizeBeforeRemoval - 1, company.getRoles().size());
    }

    @Test
    void givenMemberNotInRoles_whenRelinquishOwnership_thenThrowsException() {
        int sizeBeforeRemoval = company.getRoles().size();

        assertThrows(IllegalArgumentException.class, () ->
                company.relinquishOwnership("nobody@mail.com"));
        assertEquals(sizeBeforeRemoval, company.getRoles().size());
    }

    @Test
    void givenManager_whenRelinquishOwnership_thenThrowsException() {
        company.appointManager(FOUNDER, "manager@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS));
        int sizeBeforeRemoval = company.getRoles().size();

        assertThrows(IllegalArgumentException.class, () ->
                company.relinquishOwnership("manager@mail.com"));
        assertEquals(sizeBeforeRemoval, company.getRoles().size());
    }

    @Test
    void givenFounder_whenRelinquishOwnership_thenThrowsException() {
        int sizeBeforeRemoval = company.getRoles().size();

        assertThrows(IllegalArgumentException.class, () ->
                company.relinquishOwnership(FOUNDER));
        assertEquals(sizeBeforeRemoval, company.getRoles().size());
    }

    // ── memberHasPermission / memberIsFounder ─────────────────────────────────

    @Test
    void givenFounder_whenMemberIsFounder_thenReturnsTrue() {
        assertTrue(company.memberIsFounder(FOUNDER));
    }

    @Test
    void givenNonFounder_whenMemberIsFounder_thenReturnsFalse() {
        company.appointOwner(FOUNDER, "owner2");
        assertFalse(company.memberIsFounder("owner2"));
    }

    @Test
    void givenUnknownMember_whenMemberHasPermission_thenReturnsFalse() {
        assertFalse(company.memberHasPermission("nobody", Permissions.EVENT_EDITING));
    }

    // ===== addManagerPermission =====

    @Test
    void givenOwnerAppointedManager_whenAddPermission_thenPermissionAdded() {
        company.appointOwner(FOUNDER, "owner@mail.com");
        company.appointManager("owner@mail.com", "manager@mail.com", EnumSet.noneOf(Permissions.class));

        company.addManagerPermission("manager@mail.com", "owner@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS));

        assertTrue(company.memberHasPermission("manager@mail.com", Permissions.APPOINT_MANAGERS));
    }

    @Test
    void givenMemberIDNotInRoles_whenAddPermission_thenThrowsException() {
        company.appointOwner(FOUNDER, "owner@mail.com");

        assertThrows(IllegalArgumentException.class, () ->
                company.addManagerPermission("nobody@mail.com", "owner@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS)));
    }

    @Test
    void givenAppointedIDNotInRoles_whenAddPermission_thenThrowsException() {
        company.appointOwner(FOUNDER, "owner@mail.com");
        company.appointManager("owner@mail.com", "manager@mail.com", EnumSet.noneOf(Permissions.class));

        assertThrows(IllegalArgumentException.class, () ->
                company.addManagerPermission("manager@mail.com", "nobody@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS)));
    }

    @Test
    void givenMemberIsNotManager_whenAddPermission_thenThrowsException() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        company.appointOwner(FOUNDER, "owner2@mail.com");

        assertThrows(IllegalArgumentException.class, () ->
                company.addManagerPermission("owner1@mail.com", "owner2@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS)));
    }

    @Test
    void givenDifferentAppointer_whenAddPermission_thenThrowsException() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        company.appointOwner(FOUNDER, "owner2@mail.com");
        company.appointManager("owner1@mail.com", "manager@mail.com", EnumSet.noneOf(Permissions.class));

        assertThrows(IllegalArgumentException.class, () ->
                company.addManagerPermission("manager@mail.com", "owner2@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS)));
    }

// ===== removeManagerPermissions =====

    @Test
    void givenOwnerAppointedManager_whenRemovePermission_thenPermissionRemoved() {
        company.appointOwner(FOUNDER, "owner@mail.com");
        company.appointManager("owner@mail.com", "manager@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS));

        company.removeManagerPermissions("manager@mail.com", "owner@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS));

        assertFalse(company.memberHasPermission("manager@mail.com", Permissions.APPOINT_MANAGERS));
    }

    @Test
    void givenMemberIDNotInRoles_whenRemovePermission_thenThrowsException() {
        company.appointOwner(FOUNDER, "owner@mail.com");

        assertThrows(IllegalArgumentException.class, () ->
                company.removeManagerPermissions("nobody@mail.com", "owner@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS)));
    }

    @Test
    void givenAppointedIDNotInRoles_whenRemovePermission_thenThrowsException() {
        company.appointOwner(FOUNDER, "owner@mail.com");
        company.appointManager("owner@mail.com", "manager@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS));

        assertThrows(IllegalArgumentException.class, () ->
                company.removeManagerPermissions("manager@mail.com", "nobody@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS)));
    }

    @Test
    void givenMemberIsNotManager_whenRemovePermission_thenThrowsException() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        company.appointOwner(FOUNDER, "owner2@mail.com");

        assertThrows(IllegalArgumentException.class, () ->
                company.removeManagerPermissions("owner1@mail.com", "owner2@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS)));
    }

    @Test
    void givenDifferentAppointer_whenRemovePermission_thenThrowsException() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        company.appointOwner(FOUNDER, "owner2@mail.com");
        company.appointManager("owner1@mail.com", "manager@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS));

        assertThrows(IllegalArgumentException.class, () ->
                company.removeManagerPermissions("manager@mail.com", "owner2@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS)));
    }

    // ── setActive / setInactive ───────────────────────────────────────────────

    @Test
    void givenActiveCompany_whenSetInactive_thenIsNotActive() {
        company.setInactive();
        assertFalse(company.isActive());
    }

    @Test
    void givenInactiveCompany_whenSetActive_thenIsActive() {
        company.setInactive();
        company.setActive();
        assertTrue(company.isActive());
    }

    // ── revokeAllOwnersAndManagers ────────────────────────────────────────────

    @Test
    void givenCompanyWithOwnersAndManagers_whenRevokeAllOwnersAndManagers_thenAllRemovedExceptFounder() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        company.appointManager(FOUNDER, "manager1@mail.com", Set.of(Permissions.EVENT_EDITING));

        company.revokeAllOwnersAndManagers();

        assertEquals(1, company.getRolesByList().size(), "Only the founder should remain");
        assertTrue(company.getRolesByList().get(0) instanceof FounderRole, "The remaining role should be the FounderRole");
        assertTrue(company.memberIsFounder(FOUNDER), "The founder should still be marked as founder");
        assertFalse(company.memberHasPermission("owner1@mail.com", Permissions.APPOINT_OWNERS), "Former owner should no longer have a role");
        assertFalse(company.memberHasPermission("manager1@mail.com", Permissions.EVENT_EDITING), "Former manager should no longer have a role");
    }

    @Test
    void givenCompanyWithOnlyFounder_whenRevokeAllOwnersAndManagers_thenReturnsEmptyListAndFounderRemains() {
        List<String> result = company.revokeAllOwnersAndManagers();

        assertTrue(result.isEmpty(), "No members should be affected when only founder exists");
        assertEquals(1, company.getRolesByList().size(), "Founder role should remain");
        assertTrue(company.memberIsFounder(FOUNDER), "Founder should still be marked as founder");
    }

    @Test
    void givenCompanyWithMixedRoles_whenRevokeAllOwnersAndManagers_thenReturnsListContainingAllRevokedMembers() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        company.appointManager(FOUNDER, "manager1@mail.com", Set.of(Permissions.EVENT_EDITING));

        List<String> result = company.revokeAllOwnersAndManagers();

        assertEquals(2, result.size(), "Should return exactly 2 affected members");
        assertTrue(result.contains("owner1@mail.com"), "Result should contain the owner");
        assertTrue(result.contains("manager1@mail.com"), "Result should contain the manager");
        assertFalse(result.contains(FOUNDER), "Result should NOT contain the founder");
    }

    // ── removeMember ──────────────────────────────────────────────────────────

    @Test
    void givenMemberIsOwner_whenRemoveMember_thenRoleRemovedAndReturnsTrue() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        int sizeBefore = company.getRolesByList().size();

        boolean removed = company.removeMember("owner1@mail.com");

        assertTrue(removed, "removeMember should return true when an owner role is removed");
        assertEquals(sizeBefore - 1, company.getRolesByList().size(), "Roles size should decrease by 1");
        assertFalse(company.memberHasPermission("owner1@mail.com", Permissions.APPOINT_OWNERS), "Former owner should no longer have any role");
    }

    @Test
    void givenMemberIsManager_whenRemoveMember_thenRoleRemovedAndReturnsTrue() {
        company.appointManager(FOUNDER, "manager1@mail.com", Set.of(Permissions.EVENT_EDITING));
        int sizeBefore = company.getRolesByList().size();

        boolean removed = company.removeMember("manager1@mail.com");

        assertTrue(removed, "removeMember should return true when a manager role is removed");
        assertEquals(sizeBefore - 1, company.getRolesByList().size(), "Roles size should decrease by 1");
        assertFalse(company.memberHasPermission("manager1@mail.com", Permissions.EVENT_EDITING), "Former manager should no longer have any role");
    }

    @Test
    void givenMemberIsFounder_whenRemoveMember_thenFounderRoleRemovedAndReturnsTrue() {
        boolean removed = company.removeMember(FOUNDER);

        assertTrue(removed, "removeMember should return true even when removing the founder");
        assertEquals(0, company.getRolesByList().size(), "All roles should be removed when removing the founder of a single-member company");
        assertFalse(company.memberHasPermission(FOUNDER), "Former founder should no longer have any role in the roles map");
    }

    @Test
    void givenMemberNotInCompany_whenRemoveMember_thenReturnsFalseAndRolesUnchanged() {
        int sizeBefore = company.getRolesByList().size();

        boolean removed = company.removeMember("nobody@mail.com");

        assertFalse(removed, "removeMember should return false if member is not found");
        assertEquals(sizeBefore, company.getRolesByList().size(), "Roles size should remain unchanged");
    }
    // ── UC24 Unit Tests ───────────────────
    //  tests for suspend
    @Test
    void givenActiveCompanyAndFounderRequest_whenSuspend_thenCompanyBecomesInactive() {
        // Arrange - company is active by default
        assertTrue(company.isActive());

        // Act
        company.suspend(FOUNDER);

        // Assert
        assertFalse(company.isActive(), "Company should be inactive after suspension");
    }

    @Test
    void givenActiveCompanyAndNonFounderRequest_whenSuspend_thenThrowsIllegalArgumentException() {
        // Arrange
        company.appointOwner(FOUNDER, "other_owner@mail.com");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                company.suspend("other_owner@mail.com")
        );
        assertTrue(exception.getMessage().contains("Unauthorized"), "Should throw unauthorized error message");
        assertTrue(company.isActive(), "Company state should remain unchanged (active)");
    }

    @Test
    void givenAlreadyInactiveCompanyAndFounderRequest_whenSuspend_thenThrowsIllegalArgumentException() {
        // Arrange
        company.setInactive();
        assertFalse(company.isActive());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                company.suspend(FOUNDER)
        );
        assertTrue(exception.getMessage().contains("already inactive"), "Should complain that it is already inactive");
    }

    //  tests for reopen

    @Test
    void givenInactiveCompanyAndFounderRequest_whenReopen_thenCompanyBecomesActive() {
        // Arrange
        company.setInactive();
        assertFalse(company.isActive());

        // Act
        company.reopen(FOUNDER);

        // Assert
        assertTrue(company.isActive(), "Company should be active after reopening");
    }

    @Test
    void givenInactiveCompanyAndNonFounderRequest_whenReopen_thenThrowsIllegalArgumentException() {
        // Arrange
        company.appointOwner(FOUNDER, "other_owner@mail.com");
        company.setInactive();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                company.reopen("other_owner@mail.com")
        );
        assertTrue(exception.getMessage().contains("Unauthorized"), "Should throw unauthorized error message");
        assertFalse(company.isActive(), "Company state should remain unchanged (inactive)");
    }

    @Test
    void givenAlreadyActiveCompanyAndFounderRequest_whenReopen_thenThrowsIllegalArgumentException() {
        // Arrange - company is active by default
        assertTrue(company.isActive());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                company.reopen(FOUNDER)
        );
        assertTrue(exception.getMessage().contains("already active"), "Should complain that it is already active");
    }

    // ── getMembersToNotifyOnSuspension ───────────────────────────────────────

    @Test
    void givenCompanyWithOnlyFounder_whenGetMembersToNotifyOnSuspension_thenReturnsEmpty() {
        List<String> membersToNotify = company.getMembersToNotifyOnSuspension();

        assertTrue(membersToNotify.isEmpty(), "Founder should not be notified on suspension");
    }

    @Test
    void givenCompanyWithOwners_whenGetMembersToNotifyOnSuspension_thenReturnsOwnerIDs() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        company.appointOwner(FOUNDER, "owner2@mail.com");

        List<String> membersToNotify = company.getMembersToNotifyOnSuspension();

        assertEquals(2, membersToNotify.size(), "Both owners should be in notification list");
        assertTrue(membersToNotify.contains("owner1@mail.com"), "owner1 should be in notification list");
        assertTrue(membersToNotify.contains("owner2@mail.com"), "owner2 should be in notification list");
    }

    @Test
    void givenCompanyWithManagers_whenGetMembersToNotifyOnSuspension_thenReturnsManagerIDs() {
        company.appointManager(FOUNDER, "manager1@mail.com", EnumSet.of(Permissions.EVENT_EDITING));
        company.appointManager(FOUNDER, "manager2@mail.com", EnumSet.of(Permissions.EVENT_EDITING));

        List<String> membersToNotify = company.getMembersToNotifyOnSuspension();

        assertEquals(2, membersToNotify.size(), "Both managers should be in notification list");
        assertTrue(membersToNotify.contains("manager1@mail.com"), "manager1 should be in notification list");
        assertTrue(membersToNotify.contains("manager2@mail.com"), "manager2 should be in notification list");
    }

    @Test
    void givenCompanyWithMixedRoles_whenGetMembersToNotifyOnSuspension_thenReturnsOwnersAndManagers() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        company.appointManager(FOUNDER, "manager1@mail.com", EnumSet.of(Permissions.EVENT_EDITING));

        List<String> membersToNotify = company.getMembersToNotifyOnSuspension();

        assertEquals(2, membersToNotify.size(), "Both owner and manager should be in notification list");
        assertTrue(membersToNotify.contains("owner1@mail.com"), "owner should be in notification list");
        assertTrue(membersToNotify.contains("manager1@mail.com"), "manager should be in notification list");
        assertFalse(membersToNotify.contains(FOUNDER), "founder should NOT be in notification list");
    }

    @Test
    void givenCompanyWithFounderAndOwners_whenGetMembersToNotifyOnSuspension_thenReturnsOnlyOwnersNotFounder() {
        company.appointOwner(FOUNDER, "owner1@mail.com");

        List<String> membersToNotify = company.getMembersToNotifyOnSuspension();

        assertEquals(1, membersToNotify.size(), "Only owner should be in notification list");
        assertTrue(membersToNotify.contains("owner1@mail.com"));
        assertFalse(membersToNotify.contains(FOUNDER), "Founder should not be in notification list");
    }

    // ── getMembersToNotifyOnReopen ────────────────────────────────────────────

    @Test
    void givenCompanyWithOnlyFounder_whenGetMembersToNotifyOnReopen_thenReturnsEmpty() {
        List<String> membersToNotify = company.getMembersToNotifyOnReopen();

        assertTrue(membersToNotify.isEmpty(), "Founder should not be notified on reopen");
    }

    @Test
    void givenCompanyWithOwners_whenGetMembersToNotifyOnReopen_thenReturnsOwnerIDs() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        company.appointOwner(FOUNDER, "owner2@mail.com");

        List<String> membersToNotify = company.getMembersToNotifyOnReopen();

        assertEquals(2, membersToNotify.size(), "Both owners should be in notification list");
        assertTrue(membersToNotify.contains("owner1@mail.com"), "owner1 should be in notification list");
        assertTrue(membersToNotify.contains("owner2@mail.com"), "owner2 should be in notification list");
    }

    @Test
    void givenCompanyWithManagers_whenGetMembersToNotifyOnReopen_thenReturnsManagerIDs() {
        company.appointManager(FOUNDER, "manager1@mail.com", EnumSet.of(Permissions.EVENT_EDITING));
        company.appointManager(FOUNDER, "manager2@mail.com", EnumSet.of(Permissions.EVENT_EDITING));

        List<String> membersToNotify = company.getMembersToNotifyOnReopen();

        assertEquals(2, membersToNotify.size(), "Both managers should be in notification list");
        assertTrue(membersToNotify.contains("manager1@mail.com"), "manager1 should be in notification list");
        assertTrue(membersToNotify.contains("manager2@mail.com"), "manager2 should be in notification list");
    }

    @Test
    void givenCompanyWithMixedRoles_whenGetMembersToNotifyOnReopen_thenReturnsOwnersAndManagers() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        company.appointManager(FOUNDER, "manager1@mail.com", EnumSet.of(Permissions.EVENT_EDITING));

        List<String> membersToNotify = company.getMembersToNotifyOnReopen();

        assertEquals(2, membersToNotify.size(), "Both owner and manager should be in notification list");
        assertTrue(membersToNotify.contains("owner1@mail.com"), "owner should be in notification list");
        assertTrue(membersToNotify.contains("manager1@mail.com"), "manager should be in notification list");
        assertFalse(membersToNotify.contains(FOUNDER), "founder should NOT be in notification list");
    }

    @Test
    void givenCompanyWithFounderAndManagers_whenGetMembersToNotifyOnReopen_thenReturnsOnlyManagersNotFounder() {
        company.appointManager(FOUNDER, "manager1@mail.com", EnumSet.of(Permissions.EVENT_EDITING));

        List<String> membersToNotify = company.getMembersToNotifyOnReopen();

        assertEquals(1, membersToNotify.size(), "Only manager should be in notification list");
        assertTrue(membersToNotify.contains("manager1@mail.com"));
        assertFalse(membersToNotify.contains(FOUNDER), "Founder should not be in notification list");
    }

    // ── getManagerSubtree ─────────────────────────────────────────────────────

    @Test
    void givenOwnerWithNoSubordinates_whenGetManagerSubtree_thenReturnsOnlyOwner() {
        company.appointOwner(FOUNDER, "owner1@mail.com");

        List<String> subtree = company.getManagerSubtree("owner1@mail.com");

        assertEquals(1, subtree.size(), "Subtree should contain only the owner");
        assertTrue(subtree.contains("owner1@mail.com"), "Subtree should contain the root manager");
    }

    @Test
    void givenManagerWithDirectSubordinates_whenGetManagerSubtree_thenReturnsManagerAndSubordinates() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        company.appointManager("owner1@mail.com", "manager1@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS));
        company.appointManager("owner1@mail.com", "manager2@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS));

        List<String> subtree = company.getManagerSubtree("owner1@mail.com");

        assertEquals(3, subtree.size(), "Subtree should contain owner and both managers");
        assertTrue(subtree.contains("owner1@mail.com"), "Subtree should contain the owner");
        assertTrue(subtree.contains("manager1@mail.com"), "Subtree should contain manager1");
        assertTrue(subtree.contains("manager2@mail.com"), "Subtree should contain manager2");
    }

    @Test
    void givenHierarchyWithMultipleLevels_whenGetManagerSubtree_thenReturnsCompleteHierarchy() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        company.appointManager("owner1@mail.com", "manager1@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS));
        company.appointManager("owner1@mail.com", "manager2@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS));
        company.appointManager("manager1@mail.com", "submanager1@mail.com", EnumSet.of(Permissions.EVENT_EDITING));
        company.appointManager("manager2@mail.com", "submanager2@mail.com", EnumSet.of(Permissions.EVENT_EDITING));

        List<String> subtree = company.getManagerSubtree("owner1@mail.com");

        assertEquals(5, subtree.size(), "Subtree should contain owner, 2 managers, and 2 submanagers");
        assertTrue(subtree.contains("owner1@mail.com"));
        assertTrue(subtree.contains("manager1@mail.com"));
        assertTrue(subtree.contains("manager2@mail.com"));
        assertTrue(subtree.contains("submanager1@mail.com"));
        assertTrue(subtree.contains("submanager2@mail.com"));
    }

    @Test
    void givenManagerInHierarchy_whenGetManagerSubtree_thenReturnsOnlySubtreeNotFullHierarchy() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        company.appointManager("owner1@mail.com", "manager1@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS));
        company.appointManager("owner1@mail.com", "manager2@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS));
        company.appointManager("manager1@mail.com", "submanager1@mail.com", EnumSet.of(Permissions.EVENT_EDITING));

        List<String> subtree = company.getManagerSubtree("manager1@mail.com");

        assertEquals(2, subtree.size(), "Subtree should contain only manager1 and its subordinate");
        assertTrue(subtree.contains("manager1@mail.com"));
        assertTrue(subtree.contains("submanager1@mail.com"));
        assertFalse(subtree.contains("owner1@mail.com"), "Subtree should not contain ancestor");
        assertFalse(subtree.contains("manager2@mail.com"), "Subtree should not contain sibling");
    }

    @Test
    void givenLeafManager_whenGetManagerSubtree_thenReturnsOnlyThatManager() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        company.appointManager("owner1@mail.com", "manager1@mail.com", EnumSet.of(Permissions.EVENT_EDITING));

        List<String> subtree = company.getManagerSubtree("manager1@mail.com");

        assertEquals(1, subtree.size(), "Leaf manager subtree should contain only itself");
        assertTrue(subtree.contains("manager1@mail.com"));
    }

    @Test
    void givenComplexHierarchy_whenGetManagerSubtree_thenReturnsCompleteSubtreeWithBreadthFirst() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        company.appointManager("owner1@mail.com", "manager1@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS));
        company.appointManager("manager1@mail.com", "submanager1@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS));
        company.appointManager("manager1@mail.com", "submanager2@mail.com", EnumSet.of(Permissions.APPOINT_MANAGERS));
        company.appointManager("submanager1@mail.com", "subsubmanager1@mail.com", EnumSet.of(Permissions.EVENT_EDITING));

        List<String> subtree = company.getManagerSubtree("manager1@mail.com");

        assertEquals(4, subtree.size(), "Subtree should include manager1 and all subordinates (3 levels below manager1)");
        assertTrue(subtree.contains("manager1@mail.com"));
        assertTrue(subtree.contains("submanager1@mail.com"));
        assertTrue(subtree.contains("submanager2@mail.com"));
        assertTrue(subtree.contains("subsubmanager1@mail.com"));
    }

    @Test
    void givenNonexistentManager_whenGetManagerSubtree_thenReturnsListWithRootOnly() {
        company.appointOwner(FOUNDER, "owner1@mail.com");

        List<String> subtree = company.getManagerSubtree("nonexistent@mail.com");

        // The function includes the root manager even if it doesn't exist
        // because it always adds the root to the queue first
        assertEquals(1, subtree.size(), "Subtree should contain the root manager");
        assertTrue(subtree.contains("nonexistent@mail.com"), "Subtree should contain the nonexistent root");
    }

    @Test
    void givenFounderAsRoot_whenGetManagerSubtree_thenReturnsFounderAndAllSubordinates() {
        company.appointOwner(FOUNDER, "owner1@mail.com");
        company.appointManager("owner1@mail.com", "manager1@mail.com", EnumSet.of(Permissions.EVENT_EDITING));

        List<String> subtree = company.getManagerSubtree(FOUNDER);

        assertEquals(3, subtree.size(), "Subtree from founder should include all members under founder");
        assertTrue(subtree.contains(FOUNDER));
        assertTrue(subtree.contains("owner1@mail.com"));
        assertTrue(subtree.contains("manager1@mail.com"));
    }

    @Test
    void givenCompanyWithPurchasePolicy_whenPolicyPasses_thenDoesNotThrow() throws Exception {
        // Arrange
        domain.policies.purchase.IPurchaseRule mockPurchasePolicy = org.mockito.Mockito.mock(domain.policies.purchase.IPurchaseRule.class);
        domain.Aggregates.order.CustomerDetails context = domain.Aggregates.order.CustomerDetails.builder().build();

        // Injecting the mocked purchase policy into your existing company instance
        company.setPurchasePolicy(mockPurchasePolicy);

        // Act & Assert
        assertDoesNotThrow(() -> company.isPurchaseAllowed(context));
        org.mockito.Mockito.verify(mockPurchasePolicy).evaluate(context);
    }

    @Test
    void givenCompanyWithPurchasePolicy_whenPolicyFails_thenThrowsPolicyEvaluationException() throws Exception {
        // Arrange
        domain.policies.purchase.IPurchaseRule mockPurchasePolicy = org.mockito.Mockito.mock(domain.policies.purchase.IPurchaseRule.class);
        domain.Aggregates.order.CustomerDetails context = domain.Aggregates.order.CustomerDetails.builder().build();

        company.setPurchasePolicy(mockPurchasePolicy);

        // Simulate the purchase policy throwing your custom domain exception
        org.mockito.Mockito.doThrow(new domain.exceptions.PolicyEvaluationException("Company limit restriction reached"))
                .when(mockPurchasePolicy).evaluate(context);

        // Act & Assert
        domain.exceptions.PolicyEvaluationException exception = assertThrows(domain.exceptions.PolicyEvaluationException.class, () ->
                company.isPurchaseAllowed(context)
        );

        assertEquals("Company limit restriction reached", exception.getMessage());
    }

    @Test
    void givenCompanyWithNullPurchasePolicy_whenIsPurchaseAllowed_thenDoesNotThrow() {
        // Arrange - Edge case where no purchase policy is set for the company
        company.setPurchasePolicy(null);
        domain.Aggregates.order.CustomerDetails context = domain.Aggregates.order.CustomerDetails.builder().build();

        // Act & Assert
        assertDoesNotThrow(() -> company.isPurchaseAllowed(context));
    }

    @Test
    void givenCompanyWithDiscountPolicy_whenEvaluateDiscount_thenReturnsCorrectPercentage() {
        // Arrange
        domain.policies.discount.IDiscountRule mockDiscountPolicy = org.mockito.Mockito.mock(domain.policies.discount.IDiscountRule.class);
        domain.Aggregates.order.CustomerDetails context = domain.Aggregates.order.CustomerDetails.builder().build();

        company.setDiscountPolicy(mockDiscountPolicy);

        // Mock the discount rule to return a 15% discount
        org.mockito.Mockito.when(mockDiscountPolicy.evaluate(context)).thenReturn(15.0);

        // Act
        double discount = company.evaluateDiscount(context);

        // Assert
        assertEquals(15.0, discount, 0.001);
        org.mockito.Mockito.verify(mockDiscountPolicy).evaluate(context);
    }

    @Test
    void givenCompanyWithNullDiscountPolicy_whenEvaluateDiscount_thenReturnsZero() {
        // Arrange - Edge case where no discount policy is configured
        company.setDiscountPolicy(null);
        domain.Aggregates.order.CustomerDetails context = domain.Aggregates.order.CustomerDetails.builder().build();

        // Act
        double discount = company.evaluateDiscount(context);

        // Assert
        assertEquals(0.0, discount, 0.001);
    }

}


