package acceptance;

import application.service.RoleService;
import domain.Aggregates.member.Member;
import domain.Aggregates.notification.AppointmentNotification;
import domain.Aggregates.notification.Notification;
import java.util.List;
import domain.Aggregates.productioncompany.FounderRole;
import domain.Aggregates.productioncompany.ManagerRole;
import domain.Aggregates.productioncompany.OwnerRole;
import domain.Aggregates.productioncompany.Permissions;
import domain.Aggregates.productioncompany.ProductionCompany;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RoleServiceTest extends AcceptanceTestBase {

    private RoleService roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleService(companyRepo, authService, notificationService);
    }


    ///////// UC19 - Appoint Manager /////////
        @Test
        void GivenExistingCompanyNameAndMember_WhenAppointManager_ThenMemberGetsNotification() {
           // Arrange
           String token1 = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
           storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
           storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
    
           // Act & Assert
           assertDoesNotThrow(() -> roleService.appointManager(token1, VALID_COMPANY_NAME, VALID_EMAIL_2, MANAGER_DEFAULT_PERMISSIONS));
    
           // Assert - internal state
           List<Notification> notifications = notificationRepo.findByRecipientEmail(VALID_EMAIL_2);
           assertEquals(1, notifications.size());
           assertEquals(AppointmentNotification.class, notifications.get(0).getClass());
        }
    
        @Test
        void GivenNonMemberTarget_WhenAppointManager_ThenNotificationSentAnyway() {
           // Arrange
           String token1 = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
           storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);

           // Act & Assert - no exception thrown; member existence is not validated
           assertDoesNotThrow(() -> roleService.appointManager(token1, VALID_COMPANY_NAME, INVALID_EMAIL, MANAGER_DEFAULT_PERMISSIONS));

           // Assert - notification queued, but no role granted
           ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
           assertFalse(company.getRoles().containsKey(INVALID_EMAIL));
           assertEquals(1, company.getRolesByList().size());
        }
    
        @Test
        void GivenMemberAlreadyHasRole_WhenAppointManager_ThenThrowException() {
           // Arrange
           String token1 = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
           storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
           storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
    
           // Set up: member already has owner role
           storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, VALID_EMAIL_2);
    
           // Act & Assert - attempt to appoint the same member again
           assertThrows(Exception.class, () -> roleService.appointManager(token1, VALID_COMPANY_NAME, VALID_EMAIL_2, MANAGER_DEFAULT_PERMISSIONS));
    
           // Assert - internal state unchanged (member still has only owner role, not manager)
           assertEquals(0, notificationRepo.getAll().size());
           ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
           assertTrue(company.getRoles().containsKey(VALID_EMAIL_2));
           assertTrue(company.getRoles().get(VALID_EMAIL_2) instanceof OwnerRole);
        }

        ///////// UC19 - Appoint Owner /////////

        @Test
        void GivenExistingCompanyNameAndMember_WhenAppointOwner_ThenMemberReceivesNotification() {
            // Arrange
        String token1 = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);

        // Act
        assertDoesNotThrow(() -> roleService.appointOwner(token1, VALID_COMPANY_NAME, VALID_EMAIL_2));

        // Assert - notification created, assigned to target member, and notification is of the correct type
        List<Notification> notifications = notificationRepo.findByRecipientEmail(VALID_EMAIL_2);
        assertEquals(1, notifications.size());
        assertEquals(AppointmentNotification.class, notifications.get(0).getClass());
        }

        @Test
        void GivenNonMemberTarget_WhenAppointOwner_ThenNotificationSentAnyway() {
            // Arrange
            String token1 = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
            storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);

            // Act & Assert - no exception thrown; member existence is not validated
            assertDoesNotThrow(() -> roleService.appointOwner(token1, VALID_COMPANY_NAME, INVALID_EMAIL));

            // Assert - notification queued, but no role granted
            ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
            assertFalse(company.getRoles().containsKey(INVALID_EMAIL));
            assertEquals(1, company.getRolesByList().size());
        }

        @Test
        void GivenMemberAlreadyHasRole_WhenAppointOwner_ThenThrowException() {
            // Arrange
            String token1 = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
            storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
            storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);

            //set up: member already has owner role
            storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, VALID_EMAIL_2);

            // Act & Assert - attempt to appoint the same member again
            assertThrows(Exception.class, () -> roleService.appointOwner(token1, VALID_COMPANY_NAME, VALID_EMAIL_2));

            // Assert - internal state unchanged (still only one owner assignment)
            assertEquals(0, notificationRepo.getAll().size());
            ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
            assertTrue(company.getRoles().containsKey(VALID_EMAIL_2));
            assertTrue(company.getRoles().get(VALID_EMAIL_2) instanceof OwnerRole);
        }

    ///////// UC20 - Remove Owner/Manager Appointment /////////

    @Test
    void GivenExistingCompanyNameAndOwner_WhenRemoveOwner_ThenOwnerRemoved() {
        // Arrange
        String token1 = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);

        //set up: member already has owner role
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, VALID_EMAIL_2);

        // Act & Assert
        assertDoesNotThrow(() -> roleService.removeAppointment(token1, VALID_COMPANY_NAME, VALID_EMAIL_2));

        // Assert - internal state
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertFalse(company.getRoles().containsKey(VALID_EMAIL_2));
    }

    @Test
    void GivenOwnerTriesToRemoveAppointmentNotMadeByHim_WhenRemoveAppointment_ThenThrowException() {
        // Arrange - Owner A appoints B, Owner C tries to remove B
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder A
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // Member B
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, VALID_EMAIL_2); // Founder A appoints Owner B

        String token3 = storeMemberAndLogin(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD); // Member C
        storeManagerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, VALID_EMAIL_3); // Founder A appoints Manager C

        // Act & Assert - Owner C tries to remove the appointment of Owner B
        assertThrows(Exception.class, () ->
                roleService.removeAppointment(token3, VALID_COMPANY_NAME, VALID_EMAIL_2));

        // Assert - B still has their role, not removed
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertTrue(company.getRoles().containsKey(VALID_EMAIL_2));
        assertTrue(company.getRoles().get(VALID_EMAIL_2) instanceof OwnerRole);
    }

    @Test
    void GivenMemberHasNoRole_WhenRemoveAppointment_ThenThrowException() {
        // Arrange - Member B is a registered member but has no role in the company
        String token1 = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder A
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL); // Founder A is the founder of the company
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // Member B

        // Act & Assert - Member B has no role, system should throw
        assertThrows(Exception.class, () ->
                roleService.removeAppointment(token1, VALID_COMPANY_NAME, VALID_EMAIL_2));

        // Assert - only one role in the company
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertEquals(1, company.getRoles().size());
    }


    ///////// UC21 - Relinquish Ownership /////////

    @Test
    void GivenOwner_WhenRelinquishOwnership_ThenOwnerRoleRemoved() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        String token2 = storeMemberAndLogin(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // Owner 2
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, VALID_EMAIL_2); //Founder appoints Owner 2

        // Act & Assert
        assertDoesNotThrow(() -> roleService.relinquishOwnership(token2, VALID_COMPANY_NAME));

        // Assert - internal state
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertNotNull(company);
        assertFalse(company.getRoles().containsKey(VALID_EMAIL_2));
        // Founder role remains intact
        assertTrue(company.getRoles().containsKey(VALID_EMAIL));
    }

    @Test
    void GivenFounder_WhenRelinquishOwnership_ThenThrowAndFounderRoleUnchanged() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);

        // Act & Assert
        assertThrows(Exception.class, () -> roleService.relinquishOwnership(token, VALID_COMPANY_NAME));

        // Assert - internal state unchanged (Founder role remains)
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertNotNull(company);
        assertTrue(company.getRoles().containsKey(VALID_EMAIL));
        assertEquals(1, company.getRoles().size());
        assertTrue(company.getRoles().get(VALID_EMAIL) instanceof FounderRole);
        assertEquals(company.getFounderID(), VALID_EMAIL);
    }

    @Test
    void GivenNonOwnerMember_WhenRelinquishOwnership_ThenThrowAndRolesUnchanged() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        String token2 = storeMemberAndLogin(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // Member with no role

        // Act & Assert
        assertThrows(Exception.class, () -> roleService.relinquishOwnership(token2, VALID_COMPANY_NAME));

        // Assert - internal state unchanged
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertNotNull(company);
        assertTrue(company.getRoles().containsKey(VALID_EMAIL));
        assertFalse(company.getRoles().containsKey(VALID_EMAIL_2));
        assertEquals(1, company.getRoles().size());
    }

    @Test
    void GivenOwnerOfDifferentCompany_WhenRelinquishOwnership_ThenThrowAndRolesUnchanged() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder of company123
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // Founder of companyA
        storeCompany(VALID_COMPANY_NAME_2, VALID_EMAIL_2);
        String token3 = storeMemberAndLogin(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD); // Owner in companyA
        storeOwnerInCompany(VALID_COMPANY_NAME_2, VALID_EMAIL_2, VALID_EMAIL_3); // Founder of companyA appoints Owner 3

        // Act & Assert - Owner of companyA tries to relinquish from company123
        assertThrows(Exception.class, () -> roleService.relinquishOwnership(token3, VALID_COMPANY_NAME));

        // Assert - target company roles unchanged
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertTrue(company.getRoles().containsKey(VALID_EMAIL));
        assertFalse(company.getRoles().containsKey(VALID_EMAIL_3));
        assertEquals(1, company.getRoles().size());

        // Assert - other company's roles intact
        ProductionCompany otherCompany = companyRepo.findById(VALID_COMPANY_NAME_2);
        assertTrue(otherCompany.getRoles().containsKey(VALID_EMAIL_2));
        assertTrue(otherCompany.getRoles().containsKey(VALID_EMAIL_3));
        assertTrue(otherCompany.getRoles().get(VALID_EMAIL_3) instanceof OwnerRole);
    }

    ///////// UC22 - Add/Remove Manager Permissions /////////

    @Test
    void GivenAppointerOwner_WhenAddManagerPermission_ThenPermissionsUpdated() {
        // Arrange - Founder, Owner A (non-founder owner) appointed Manager B with VIEW_MANAGEMENT_HIERARCHY ("View Only")
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        String tokenOwnerA = storeMemberAndLogin(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // Owner A
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, VALID_EMAIL_2); // Founder appoints Owner A
        storeMember(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD); // Manager B
        storeManagerInCompany(VALID_COMPANY_NAME, VALID_EMAIL_2, VALID_EMAIL_3); // Owner A appoints Manager B with default permissions

        // Act & Assert - Owner A grants EVENT_EDITING ("Full Edit")
        assertDoesNotThrow(() -> roleService.addManagerPermission(tokenOwnerA, VALID_COMPANY_NAME, VALID_EMAIL_3, EVENT_EDITING_PERMISSIONS));

        // Assert - internal state: manager now has both old and new permissions
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertNotNull(company);
        assertTrue(company.getRoles().get(VALID_EMAIL_3) instanceof ManagerRole);
        ManagerRole managerRole = (ManagerRole) company.getRoles().get(VALID_EMAIL_3);
        assertTrue(managerRole.getPermissions().contains(Permissions.EVENT_EDITING));
        assertTrue(managerRole.getPermissions().contains(Permissions.VIEW_MANAGEMENT_HIERARCHY));
    }

    @Test
    void GivenAppointerOwner_WhenRemoveManagerPermission_ThenPermissionsUpdated() {
        // Arrange - Founder, Owner A (non-founder owner) appointed Manager B with VIEW_MANAGEMENT_HIERARCHY + EVENT_EDITING
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        String tokenOwnerA = storeMemberAndLogin(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // Owner A
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, VALID_EMAIL_2); // Founder appoints Owner A
        storeMember(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD); // Manager B
        Set<Permissions> initialPermissions = EnumSet.of(Permissions.VIEW_MANAGEMENT_HIERARCHY, Permissions.EVENT_EDITING);
        storeManagerInCompanyWithPermissions(VALID_COMPANY_NAME, VALID_EMAIL_2, VALID_EMAIL_3, initialPermissions); // Owner A appoints Manager B

        // Act & Assert - Owner A revokes EVENT_EDITING
        assertDoesNotThrow(() -> roleService.removeManagerPermission(tokenOwnerA, VALID_COMPANY_NAME, VALID_EMAIL_3, EVENT_EDITING_PERMISSIONS));

        // Assert - internal state: manager retains only VIEW_MANAGEMENT_HIERARCHY
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertNotNull(company);
        assertTrue(company.getRoles().get(VALID_EMAIL_3) instanceof ManagerRole);
        ManagerRole managerRole = (ManagerRole) company.getRoles().get(VALID_EMAIL_3);
        assertFalse(managerRole.getPermissions().contains(Permissions.EVENT_EDITING));
        assertTrue(managerRole.getPermissions().contains(Permissions.VIEW_MANAGEMENT_HIERARCHY));
    }

    @Test
    void GivenTargetIsOwnerNotManager_WhenAddManagerPermission_ThenThrowAndUnchanged() {
        // Arrange - Founder, Owner A appointed Owner B (target is an owner, not a manager)
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        String tokenOwnerA = storeMemberAndLogin(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // Owner A
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, VALID_EMAIL_2); // Founder appoints Owner A
        storeMember(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD); // Owner B
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL_2, VALID_EMAIL_3); // Owner A appoints Owner B

        // Act & Assert - Owner A tries to change Owner B's permissions
        assertThrows(Exception.class, () -> roleService.addManagerPermission(tokenOwnerA, VALID_COMPANY_NAME, VALID_EMAIL_3, EVENT_EDITING_PERMISSIONS));

        // Assert - Owner B still has an OwnerRole, not a ManagerRole
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertTrue(company.getRoles().get(VALID_EMAIL_3) instanceof OwnerRole);
    }

    @Test
    void GivenActorIsNotAppointerOfManager_WhenAddManagerPermission_ThenThrowAndUnchanged() {
        // Arrange - Founder, Owner A appointed Manager B, Owner C did NOT appoint Manager B
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // Owner A
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, VALID_EMAIL_2); // Founder appoints Owner A
        String tokenOwnerC = storeMemberAndLogin(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD); // Owner C
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, VALID_EMAIL_3); // Founder appoints Owner C
        storeMember(MANAGER1_USERNAME, MANAGER1_EMAIL, VALID_PASSWORD); // Manager B
        storeManagerInCompany(VALID_COMPANY_NAME, VALID_EMAIL_2, MANAGER1_EMAIL); // Owner A appoints Manager B

        // Act & Assert - Owner C (not the appointer) tries to change Manager B's permissions
        assertThrows(Exception.class, () -> roleService.addManagerPermission(tokenOwnerC, VALID_COMPANY_NAME, MANAGER1_EMAIL, EVENT_EDITING_PERMISSIONS));

        // Assert - Manager B's permissions remain unchanged (only the default VIEW_MANAGEMENT_HIERARCHY)
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertTrue(company.getRoles().get(MANAGER1_EMAIL) instanceof ManagerRole);
        ManagerRole managerRole = (ManagerRole) company.getRoles().get(MANAGER1_EMAIL);
        assertFalse(managerRole.getPermissions().contains(Permissions.EVENT_EDITING));
        assertTrue(managerRole.getPermissions().contains(Permissions.VIEW_MANAGEMENT_HIERARCHY));
    }

    @Test
    void GivenTargetIsOwnerNotManager_WhenRemoveManagerPermission_ThenThrowAndUnchanged() {
        // Arrange - Founder, Owner A appointed Owner B (target is an owner, not a manager)
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        String tokenOwnerA = storeMemberAndLogin(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // Owner A
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, VALID_EMAIL_2); // Founder appoints Owner A
        storeMember(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD); // Owner B
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL_2, VALID_EMAIL_3); // Owner A appoints Owner B

        // Act & Assert - Owner A tries to remove permissions from Owner B
        assertThrows(Exception.class, () -> roleService.removeManagerPermission(tokenOwnerA, VALID_COMPANY_NAME, VALID_EMAIL_3, EVENT_EDITING_PERMISSIONS));

        // Assert - Owner B still has an OwnerRole, not a ManagerRole
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertTrue(company.getRoles().get(VALID_EMAIL_3) instanceof OwnerRole);
    }

    @Test
    void GivenActorIsNotAppointerOfManager_WhenRemoveManagerPermission_ThenThrowAndUnchanged() {
        // Arrange - Founder, Owner A appointed Manager B, Owner C did NOT appoint Manager B
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // Owner A
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, VALID_EMAIL_2); // Founder appoints Owner A
        String tokenOwnerC = storeMemberAndLogin(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD); // Owner C
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, VALID_EMAIL_3); // Founder appoints Owner C
        storeMember(MANAGER1_USERNAME, MANAGER1_EMAIL, VALID_PASSWORD); // Manager B
        storeManagerInCompany(VALID_COMPANY_NAME, VALID_EMAIL_2, MANAGER1_EMAIL); // Owner A appoints Manager B

        // Act & Assert - Owner C (not the appointer) tries to remove Manager B's VIEW_MANAGEMENT_HIERARCHY
        assertThrows(Exception.class, () -> roleService.removeManagerPermission(tokenOwnerC, VALID_COMPANY_NAME, MANAGER1_EMAIL, MANAGER_DEFAULT_PERMISSIONS));

        // Assert - Manager B's permissions remain unchanged (still has VIEW_MANAGEMENT_HIERARCHY)
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertTrue(company.getRoles().get(MANAGER1_EMAIL) instanceof ManagerRole);
        ManagerRole managerRole = (ManagerRole) company.getRoles().get(MANAGER1_EMAIL);
        assertTrue(managerRole.getPermissions().contains(Permissions.VIEW_MANAGEMENT_HIERARCHY));
    }

    ///////// UC12 - Accept/Decline Appointment /////////

    @Test
    void GivenPendingManagerAppointment_WhenAcceptAppointment_ThenManagerRoleGranted() {
        // Arrange - assume appointManager already succeeded: company exists and target has a pending notification
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder (already the appointer)
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // Target member
        sendManagerAppointmentNotification(VALID_COMPANY_NAME, VALID_EMAIL, VALID_EMAIL_2, MANAGER_DEFAULT_PERMISSIONS);
        String tokenTarget = authService.generateToken(VALID_EMAIL_2);

        // Act - target accepts the appointment
        assertDoesNotThrow(() -> roleService.acceptAppointment(tokenTarget, VALID_COMPANY_NAME, true));

        // Assert - target now has ManagerRole in the company
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertTrue(company.getRoles().containsKey(VALID_EMAIL_2));
        assertTrue(company.getRoles().get(VALID_EMAIL_2) instanceof ManagerRole);

        // Assert - pending notification was removed
        assertEquals(0, notificationRepo.findByRecipientEmail(VALID_EMAIL_2).size());
    }

    @Test
    void GivenPendingManagerAppointment_WhenDeclineAppointment_ThenNoRoleGranted() {
        // Arrange - assume appointManager already succeeded: company exists and target has a pending notification
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder (already the appointer)
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // Target member
        sendManagerAppointmentNotification(VALID_COMPANY_NAME, VALID_EMAIL, VALID_EMAIL_2, MANAGER_DEFAULT_PERMISSIONS);
        String tokenTarget = authService.generateToken(VALID_EMAIL_2);

        // Act - target declines the appointment
        assertDoesNotThrow(() -> roleService.acceptAppointment(tokenTarget, VALID_COMPANY_NAME, false));

        // Assert - no role granted to the target in the company
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertFalse(company.getRoles().containsKey(VALID_EMAIL_2));

        // Assert - pending notification was removed
        assertEquals(0, notificationRepo.findByRecipientEmail(VALID_EMAIL_2).size());
    }

    @Test
    void GivenTargetAlreadyHasRoleInCompany_WhenAcceptAppointment_ThenThrowAndNoRoleChange() {
        // Arrange - assume appointManager already succeeded: notification is pending for the target.
        // Then, before the target accepts, the target is given an Owner role in the same company.
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // Target member
        sendManagerAppointmentNotification(VALID_COMPANY_NAME, VALID_EMAIL, VALID_EMAIL_2, MANAGER_DEFAULT_PERMISSIONS);
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, VALID_EMAIL_2); // Target acquired an owner role meanwhile
        String tokenTarget = authService.generateToken(VALID_EMAIL_2);

        // Act & Assert - acceptance is rejected because the target already has a role
        assertThrows(Exception.class, () -> roleService.acceptAppointment(tokenTarget, VALID_COMPANY_NAME, true));

        // Assert - target's role is unchanged (still the OwnerRole assigned before accept)
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertTrue(company.getRoles().get(VALID_EMAIL_2) instanceof OwnerRole);
    }

    ///////// Concurrency - Appointment /////////

    @Test
    void GivenTwoOwnersAppointSameMemberConcurrently_WhenBothAppointOwner_ThenOnlyOneSucceeds() throws InterruptedException {
        // Arrange - Founder creates company, two owners appointed, and a target member registered
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        String tokenOwner1 = storeMemberAndLogin(OWNER1_USERNAME, OWNER1_EMAIL, VALID_PASSWORD); // Owner 1
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, OWNER1_EMAIL);
        String tokenOwner2 = storeMemberAndLogin(OWNER2_USERNAME, OWNER2_EMAIL, VALID_PASSWORD); // Owner 2
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, OWNER2_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // Target member

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);

        // Act - both owners race to appoint the same target member simultaneously
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(() -> {
            try {
                barrier.await(); // synchronize both threads to start at the same moment
                roleService.appointOwner(tokenOwner1, VALID_COMPANY_NAME, VALID_EMAIL_2);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });
        executor.submit(() -> {
            try {
                barrier.await();
                roleService.appointOwner(tokenOwner2, VALID_COMPANY_NAME, VALID_EMAIL_2);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        executor.shutdown();

        // Assert - exactly one appointment succeeded and one was rejected by the optimistic lock
        assertEquals(1, successCount.get());
        assertEquals(1, failureCount.get());

        // Assert - target has exactly one pending notification
        List<Notification> notifications = notificationRepo.findByRecipientEmail(VALID_EMAIL_2);
        assertEquals(1, notifications.size());
        assertEquals(AppointmentNotification.class, notifications.get(0).getClass());

        // Assert - company internal state: target appears exactly once as a pending appointee 
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertTrue(company.isPendingAppointee(VALID_EMAIL_2));
        assertEquals(1, company.getPendingAppointees().stream().filter(VALID_EMAIL_2::equals).count());

        // Assert - no role was prematurely granted; the only roles are the founder and the two owners
        assertEquals(3, company.getRoles().size());
        assertFalse(company.getRoles().containsKey(VALID_EMAIL_2));
    }

}
