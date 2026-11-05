package acceptance;

import static org.junit.jupiter.api.Assertions.*;

import application.dto.ProductionCompanyDTO;
import application.dto.SalesReportDTO;
import application.service.ProductionCompanyService;
import domain.Aggregates.event.Event;
import domain.Aggregates.productioncompany.FounderRole;
import domain.Aggregates.productioncompany.Permissions;
import domain.Aggregates.productioncompany.ProductionCompany;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ProductionCompanyServiceTest extends AcceptanceTestBase {

    private ProductionCompanyService productionCompanyService;

    @BeforeEach
    void setUp() {
        productionCompanyService = new ProductionCompanyService(
                companyRepo, authService, eventRepo,
                notificationService, discountPolicyFactory, purchasePolicyFactory);
    }

    ///////// UC13 - Open Production Company /////////

    @Test
    void GivenUniqueCompanyName_WhenOpenProductionCompany_ThenCompanyCreatedAndFounderRoleAssigned() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        // Act
        ProductionCompanyDTO result = productionCompanyService.openProductionCompany(VALID_COMPANY_NAME, token);

        // Assert - return value
        assertNotNull(result);
        assertEquals(VALID_COMPANY_NAME, result.name);
        assertTrue(result.isActive);

        // Assert - internal state
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertNotNull(company);
        assertTrue(company.isActive());
        assertEquals(VALID_EMAIL, company.getFounderID());
        // Check if the founder role is created and assigned
        assertTrue(company.getRoles().containsKey(VALID_EMAIL));
        assertTrue(company.getRoles().get(VALID_EMAIL) instanceof FounderRole);
    }

    @Test
    void GivenExistingCompanyName_WhenOpenProductionCompany_ThenThrowAndNoNewCompanyCreated() {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        String token = storeMemberAndLogin(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);

        // Act & Assert
        assertThrows(Exception.class, () -> productionCompanyService.openProductionCompany(VALID_COMPANY_NAME, token));

        // Assert - internal state unchanged
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertEquals(VALID_EMAIL, company.getFounderID()); // Valid founder ID is the email of the first member
        // Check that the new member does not have a role
        assertFalse(company.getRoles().containsKey(VALID_EMAIL_2));
    }

    ///////// UC23 - View Role and Permission Info /////////

    @Test
    void GivenFounderActor_WhenGetAllRoles_ThenReturnsHierarchyWithAllMembers() {
        // Arrange - founder opens company; owner1 and manager1 are appointed by founder
        String tokenFounder = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeMember(OWNER1_USERNAME, OWNER1_EMAIL, VALID_PASSWORD);
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, OWNER1_EMAIL);
        storeMember(MANAGER1_USERNAME, MANAGER1_EMAIL, VALID_PASSWORD);
        storeManagerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, MANAGER1_EMAIL);

        // Act
        Map<String, List<String>> hierarchy = productionCompanyService.getAllRoles(VALID_COMPANY_NAME, tokenFounder);

        // Assert
        assertNotNull(hierarchy);
        assertTrue(hierarchy.containsKey("Founder"));
        assertEquals(1, hierarchy.get("Founder").size());
        assertTrue(hierarchy.containsKey(VALID_EMAIL));
        assertTrue(hierarchy.get(VALID_EMAIL).contains(OWNER1_EMAIL));
        assertTrue(hierarchy.get(VALID_EMAIL).contains(MANAGER1_EMAIL));
    }

    @Test
    void GivenOwnerActor_WhenGetAllRoles_ThenReturnsHierarchyWithAllMembers() {
        // Arrange - founder opens company; owner1 (logged in) and manager1 are appointed by founder
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        String tokenOwner = storeMemberAndLogin(OWNER1_USERNAME, OWNER1_EMAIL, VALID_PASSWORD);
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, OWNER1_EMAIL);
        storeMember(MANAGER1_USERNAME, MANAGER1_EMAIL, VALID_PASSWORD);
        storeManagerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, MANAGER1_EMAIL);

        // Act
        Map<String, List<String>> hierarchy = productionCompanyService.getAllRoles(VALID_COMPANY_NAME, tokenOwner);

        // Assert
        assertNotNull(hierarchy);
        assertTrue(hierarchy.containsKey("Founder"));
        assertEquals(1, hierarchy.get("Founder").size());
        assertTrue(hierarchy.containsKey(VALID_EMAIL));
        assertTrue(hierarchy.get(VALID_EMAIL).contains(OWNER1_EMAIL));
        assertTrue(hierarchy.get(VALID_EMAIL).contains(MANAGER1_EMAIL));
    }

    @Test
    void GivenManagerActor_WhenGetAllRoles_ThenReturnsHierarchyWithAllMembers() {
        // Arrange - founder opens company; manager1 (logged in) appointed by founder with VIEW_MANAGEMENT_HIERARCHY
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeMember(OWNER1_USERNAME, OWNER1_EMAIL, VALID_PASSWORD);
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, OWNER1_EMAIL);
        String tokenManager = storeMemberAndLogin(MANAGER1_USERNAME, MANAGER1_EMAIL, VALID_PASSWORD);
        storeManagerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, MANAGER1_EMAIL); // Manager has VIEW_MANAGEMENT_HIERARCHY permission by default

        // Act
        Map<String, List<String>> hierarchy = productionCompanyService.getAllRoles(VALID_COMPANY_NAME, tokenManager);

        // Assert
        assertNotNull(hierarchy);
        assertTrue(hierarchy.containsKey("Founder"));
        assertEquals(1, hierarchy.get("Founder").size());
        assertTrue(hierarchy.containsKey(VALID_EMAIL));
        assertTrue(hierarchy.get(VALID_EMAIL).contains(OWNER1_EMAIL));
        assertTrue(hierarchy.get(VALID_EMAIL).contains(MANAGER1_EMAIL));
    }

    @Test
    void GivenManagerWithoutViewPermission_WhenGetAllRoles_ThenThrowException() {
        // Arrange - manager1 has no permissions (no VIEW_MANAGEMENT_HIERARCHY)
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        String tokenManager = storeMemberAndLogin(MANAGER1_USERNAME, MANAGER1_EMAIL, VALID_PASSWORD);
        Set<Permissions> noHierarchyPermissions = EnumSet.of(Permissions.APPOINT_OWNERS);
        storeManagerInCompanyWithPermissions(VALID_COMPANY_NAME, VALID_EMAIL, MANAGER1_EMAIL, noHierarchyPermissions);

        // Act & Assert
        assertThrows(Exception.class, () -> productionCompanyService.getAllRoles(VALID_COMPANY_NAME, tokenManager));    
    }

    @Test
    void GivenMemberWithoutRoleInCompany_WhenGetAllRoles_ThenThrowException() {
        // Arrange - owner1 is a registered member but has NO role in company123
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        String tokenOwner = storeMemberAndLogin(OWNER1_USERNAME, OWNER1_EMAIL, VALID_PASSWORD); // No role in company123

        // Act & Assert
        assertThrows(Exception.class, () -> productionCompanyService.getAllRoles(VALID_COMPANY_NAME, tokenOwner));
    }

    @Test
    void GivenOwnerOfDifferentCompany_WhenGetAllRoles_ThenThrowException() {
        // Arrange - owner1 is an Owner of companyA but has NO role in company123
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder of company123
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // Founder of companyA
        storeCompany(VALID_COMPANY_NAME_2, VALID_EMAIL_2);
        String tokenOwner = storeMemberAndLogin(OWNER1_USERNAME, OWNER1_EMAIL, VALID_PASSWORD); // Owner only in companyA
        storeOwnerInCompany(VALID_COMPANY_NAME_2, VALID_EMAIL_2, OWNER1_EMAIL);

        // Act & Assert - owner of companyA tries to view roles of company123
        assertThrows(Exception.class, () -> productionCompanyService.getAllRoles(VALID_COMPANY_NAME, tokenOwner));
    }

    ///////// UC24 - Suspend Company /////////

    @Test
    void GivenFounderActor_WhenSuspendProductionCompany_ThenCompanyAndEventsInactiveAndMembersNotified() {
        // Arrange - founder opens company; owner and manager are appointed; an active event exists for the company
        String tokenFounder = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeMember(OWNER1_USERNAME, OWNER1_EMAIL, VALID_PASSWORD);
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, OWNER1_EMAIL);
        storeMember(MANAGER1_USERNAME, MANAGER1_EMAIL, VALID_PASSWORD);
        storeManagerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, MANAGER1_EMAIL);
        Event activeEvent = storeEvent(VALID_COMPANY_NAME, true);

        // Act
        ProductionCompanyDTO result = productionCompanyService.suspendProductionCompany(VALID_COMPANY_NAME, tokenFounder);

        // Assert - return value
        assertNotNull(result);
        assertFalse(result.isActive);

        // Assert - company is now inactive
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertFalse(company.isActive());

        // Assert - the company's event was deactivated
        Event reloadedEvent = eventRepo.findById(activeEvent.getEventId());
        assertFalse(reloadedEvent.isActive());

        // Assert - notifications were stored for owner and manager (offline notifier queues them in the repo)
        assertEquals(1, notificationRepo.findByRecipientEmail(OWNER1_EMAIL).size());
        assertEquals(1, notificationRepo.findByRecipientEmail(MANAGER1_EMAIL).size());
    }

    @Test
    void GivenNonFounderOwnerActor_WhenSuspendProductionCompany_ThenThrowAndCompanyStaysActive() {
        // Arrange - founder opens company; owner1 (non-founder) is appointed and logs in
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        String tokenOwner = storeMemberAndLogin(OWNER1_USERNAME, OWNER1_EMAIL, VALID_PASSWORD); // Non-founder owner
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, OWNER1_EMAIL);

        // Act & Assert - non-founder owner cannot suspend the company
        assertThrows(Exception.class, () -> productionCompanyService.suspendProductionCompany(VALID_COMPANY_NAME, tokenOwner));

        // Assert - company remains active
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertTrue(company.isActive());
    }

    @Test
    void GivenAlreadyInactiveCompany_WhenSuspendProductionCompany_ThenThrow() {
        // Arrange - founder opens company; the company is set inactive directly via the repo
        String tokenFounder = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        company.setInactive();
        companyRepo.store(company);

        // Act & Assert - suspending an already-inactive company throws
        assertThrows(Exception.class, () -> productionCompanyService.suspendProductionCompany(VALID_COMPANY_NAME, tokenFounder));

        // Assert - company is still inactive
        ProductionCompany reloaded = companyRepo.findById(VALID_COMPANY_NAME);
        assertFalse(reloaded.isActive());
    }

    ///////// UC25 - Reopen Closed Company /////////

    @Test
    void GivenFounderActor_WhenReopenSuspendedProductionCompany_ThenCompanyActiveAndMembersNotified() {
        // Arrange - founder opens company; owner and manager appointed; company set inactive directly via repo
        String tokenFounder = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeMember(OWNER1_USERNAME, OWNER1_EMAIL, VALID_PASSWORD);
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, OWNER1_EMAIL);
        storeMember(MANAGER1_USERNAME, MANAGER1_EMAIL, VALID_PASSWORD);
        storeManagerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, MANAGER1_EMAIL);
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME); // refetch to get latest version
        company.setInactive();
        companyRepo.store(company);

        // Act
        ProductionCompanyDTO result = productionCompanyService.reopenProductionCompany(VALID_COMPANY_NAME, tokenFounder);

        // Assert - return value
        assertNotNull(result);
        assertTrue(result.isActive);

        // Assert - company is now active
        ProductionCompany reloaded = companyRepo.findById(VALID_COMPANY_NAME);
        assertTrue(reloaded.isActive());

        // Assert - notifications were stored for owner and manager (offline notifier queues them in the repo)
        assertEquals(1, notificationRepo.findByRecipientEmail(OWNER1_EMAIL).size());
        assertEquals(1, notificationRepo.findByRecipientEmail(MANAGER1_EMAIL).size());
    }

    @Test
    void GivenNonFounderOwnerActor_WhenReopenProductionCompany_ThenThrowAndCompanyStaysInactive() {
        // Arrange - founder opens company; non-founder owner is appointed; company is set inactive via repo
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        String tokenOwner = storeMemberAndLogin(OWNER1_USERNAME, OWNER1_EMAIL, VALID_PASSWORD); // Non-founder owner
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, OWNER1_EMAIL);
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME); // refetch to get latest version
        company.setInactive();
        companyRepo.store(company);

        // Act & Assert - non-founder owner cannot reopen the company
        assertThrows(Exception.class, () -> productionCompanyService.reopenProductionCompany(VALID_COMPANY_NAME, tokenOwner));

        // Assert - company remains inactive
        ProductionCompany reloaded = companyRepo.findById(VALID_COMPANY_NAME);
        assertFalse(reloaded.isActive());
    }

    @Test
    void GivenAlreadyActiveCompany_WhenReopenProductionCompany_ThenThrow() {
        // Arrange - founder opens company (active by default)
        String tokenFounder = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);

        // Act & Assert - reopen on an already-active company throws
        assertThrows(Exception.class, () -> productionCompanyService.reopenProductionCompany(VALID_COMPANY_NAME, tokenFounder));

        // Assert - company is still active
        ProductionCompany reloaded = companyRepo.findById(VALID_COMPANY_NAME);
        assertTrue(reloaded.isActive());
    }

    ///////// UC18 - Generate Sales Report /////////

    @Test
    void GivenManagerWithSubtree_WhenGenerateSalesReport_ThenTotalsIncludeEntireSubtree() {
        // Arrange - Founder A appoints Manager B; both own one event each with real orders
        String tokenA = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder A
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // Manager B
        storeManagerInCompanyWithPermissions(VALID_COMPANY_NAME, VALID_EMAIL, VALID_EMAIL_2,
                EnumSet.of(Permissions.GENERATE_SALES_REPORT));

        // Founder A's event sells 12 tickets across 2 orders (80 + 120 = 200)
        Event eventA = storeEventWithManager(VALID_COMPANY_NAME, true, "eventA", VALID_EMAIL);
        storeOrderForEvent("orderA1", eventA.getEventId(), 5, 80.0);
        storeOrderForEvent("orderA2", eventA.getEventId(), 7, 120.0);

        // Manager B's event sells 8 tickets across 1 order (500)
        Event eventB = storeEventWithManager(VALID_COMPANY_NAME, true, "eventB", VALID_EMAIL_2);
        storeOrderForEvent("orderB1", eventB.getEventId(), 8, 500.0);

        // Act - Founder A generates their sales report (subtree = {A, B})
        SalesReportDTO report = productionCompanyService.generateSalesReport(tokenA, VALID_COMPANY_NAME);

        // Assert - return value: totals are the sum across the entire subtree (A + B)
        assertNotNull(report);
        assertEquals(VALID_EMAIL, report.managerId);
        assertEquals(20, report.totalTicketsSold); // 5 + 7 + 8
        assertEquals(700.0, report.totalRevenue, 0.001); // 80 + 120 + 500
        assertTrue(report.includedManagerIds.contains(VALID_EMAIL), "Subtree must include A");
        assertTrue(report.includedManagerIds.contains(VALID_EMAIL_2), "Subtree must include B (appointed by A)");

        // Assert - internal state: company roles unchanged (report is read-only)
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertEquals(2, company.getRoles().size()); // Founder A + Manager B
        assertTrue(company.getRoles().containsKey(VALID_EMAIL));
        assertTrue(company.getRoles().containsKey(VALID_EMAIL_2));
    }

    @Test
    void GivenManagerWithSiblings_WhenGenerateSalesReport_ThenOnlyOwnSubtreeIncluded() {
        // Arrange - Founder A appoints Owner B1 and Manager B2 (siblings — neither appoints the other)
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder A
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        String tokenB1 = storeMemberAndLogin(OWNER1_USERNAME, OWNER1_EMAIL, VALID_PASSWORD); // Owner B1
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, OWNER1_EMAIL);
        storeMember(MANAGER1_USERNAME, MANAGER1_EMAIL, VALID_PASSWORD); // Manager B2 (sibling of B1)
        storeManagerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, MANAGER1_EMAIL);

        // B1's event sells 4 tickets for 100; B2's event sells 30 tickets for 300 — B2's
        // numbers must not appear in B1's report.
        Event eventB1 = storeEventWithManager(VALID_COMPANY_NAME, true, "eventB1", OWNER1_EMAIL);
        storeOrderForEvent("orderB1", eventB1.getEventId(), 4, 100.0);

        Event eventB2 = storeEventWithManager(VALID_COMPANY_NAME, true, "eventB2", MANAGER1_EMAIL);
        storeOrderForEvent("orderB2", eventB2.getEventId(), 30, 300.0);

        // Act - B1 generates their own report
        SalesReportDTO report = productionCompanyService.generateSalesReport(tokenB1, VALID_COMPANY_NAME);

        // Assert - return value: only B1's own data (B2 is a sibling, not a subordinate)
        assertNotNull(report);
        assertEquals(OWNER1_EMAIL, report.managerId);
        assertEquals(4, report.totalTicketsSold, "B1's report must not include sibling B2's tickets");
        assertEquals(100.0, report.totalRevenue, 0.001);
        assertTrue(report.includedManagerIds.contains(OWNER1_EMAIL));
        assertFalse(report.includedManagerIds.contains(MANAGER1_EMAIL), "Sibling B2 must not appear in B1's subtree");

        // Assert - internal state: both roles still exist in the company
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertTrue(company.getRoles().containsKey(OWNER1_EMAIL));
        assertTrue(company.getRoles().containsKey(MANAGER1_EMAIL));
    }

    @Test
    void GivenManagerWithoutGenerateSalesReportPermission_WhenGenerateSalesReport_ThenThrowException() {
        // Arrange - manager has only VIEW_MANAGEMENT_HIERARCHY, not GENERATE_SALES_REPORT
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        String tokenManager = storeMemberAndLogin(MANAGER1_USERNAME, MANAGER1_EMAIL, VALID_PASSWORD);
        storeManagerInCompanyWithPermissions(VALID_COMPANY_NAME, VALID_EMAIL, MANAGER1_EMAIL,
                EnumSet.of(Permissions.VIEW_MANAGEMENT_HIERARCHY)); // no GENERATE_SALES_REPORT

        // Act & Assert - manager without permission cannot generate a report
        assertThrows(IllegalArgumentException.class,
                () -> productionCompanyService.generateSalesReport(tokenManager, VALID_COMPANY_NAME));

        // Assert - internal state: manager role unchanged
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertTrue(company.getRoles().containsKey(MANAGER1_EMAIL));
        assertFalse(company.getRoles().get(MANAGER1_EMAIL).hasPermission(Permissions.GENERATE_SALES_REPORT));
    }

    @Test
    void GivenManagerWithNoSales_WhenGenerateSalesReport_ThenReportIsEmpty() {
        // Arrange - founder with no subordinates and no events/orders
        String tokenFounder = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); // Founder
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);

        // Act - founder generates their report (no tickets sold)
        SalesReportDTO report = productionCompanyService.generateSalesReport(tokenFounder, VALID_COMPANY_NAME);

        // Assert - return value: empty totals
        assertNotNull(report);
        assertEquals(VALID_EMAIL, report.managerId);
        assertEquals(0, report.totalTicketsSold);
        assertEquals(0.0, report.totalRevenue, 0.001);
        assertTrue(report.includedManagerIds.contains(VALID_EMAIL));

        // Assert - internal state: company remains unchanged (report is read-only)
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertEquals(1, company.getRoles().size()); // only the founder role
        assertTrue(company.getRoles().containsKey(VALID_EMAIL));
    }

    ///////// Race Condition Tests /////////

    @Test
    void givenMultipleUsers_whenConcurrentOpenCompanyWithSameName_thenOnlyOneSucceeds() throws InterruptedException {
        // Arrange
        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            String email = "founder" + i + "@mail.com";
            storeMember(email, email, VALID_PASSWORD);
            String token = authService.generateToken(email);

            executor.submit(() -> {
                try {
                    startLatch.await();
                    productionCompanyService.openProductionCompany(VALID_COMPANY_NAME, token);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Act
        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertTrue(completed, "Test timed out");
        assertEquals(1, successCount.get(), "Exactly one company creation should succeed");
        assertEquals(numberOfThreads - 1, failureCount.get(), "All other creations should fail");

        // Verify only one company exists with that name
        assertEquals(1, companyRepo.getAll().size(), "Only one company should be in the repository");
    }

    //// UC16  set purchase & discount Policies
    @Test
    void givenCompanyWithAuthorizedManager_whenSetCompanyPurchasePolicy_thenPolicyIsApplied() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);

        // Grant the manager explicit CHANGE_PURCHASE_POLICY permission
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL,
                EnumSet.of(Permissions.CHANGE_PURCHASE_POLICY));

        // Act
        ProductionCompanyDTO result = assertDoesNotThrow(() ->
                productionCompanyService.setCompanyPurchasePolicy(token, company.getName(), MIN_AGE_18_POLICY));

        // Assert
        assertNotNull(result);
        assertEquals(company.getName(), result.name);

        // Assert - database internal state verified
        ProductionCompany storedCompany = companyRepo.findById(company.getName());
        assertNotNull(storedCompany);
        assertNotNull(storedCompany.getPurchasePolicy());
    }

    @Test
    void givenManagerWithoutPermission_whenSetCompanyPurchasePolicy_thenThrowsIllegalArgumentException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);

        // Manager lacks CHANGE_PURCHASE_POLICY (assuming default/empty permissions)
        storeManagerInCompany(company.getName(), company.getFounderID(), VALID_EMAIL);

        // Act & Assert - should fail because manager is unauthorized
        assertThrows(IllegalArgumentException.class, () ->
                productionCompanyService.setCompanyPurchasePolicy(token, company.getName(), MIN_AGE_18_POLICY));

        // Assert - internal state remains unchanged
        ProductionCompany storedCompany = companyRepo.findById(company.getName());
        assertNotEquals(MIN_AGE_18_POLICY, storedCompany.getPurchasePolicy());
    }

    @Test
    void givenCompanyWithAuthorizedManager_whenSetCompanyDiscountPolicy_thenPolicyIsApplied() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);

        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL,
                EnumSet.of(Permissions.CHANGE_DISCOUNT_POLICY));

        // Act
        ProductionCompanyDTO result = assertDoesNotThrow(() ->
                productionCompanyService.setCompanyDiscountPolicy(token, company.getName(), DISCOUNT_COMPOSITE_SUM));

        // Assert
        assertNotNull(result);
        assertEquals(company.getName(), result.name);

        // Assert - database internal state verified
        ProductionCompany storedCompany = companyRepo.findById(company.getName());
        assertNotNull(storedCompany);
        assertNotNull(storedCompany.getDiscountPolicy());
    }

    @Test
    void givenManagerWithoutPermission_whenSetCompanyDiscountPolicy_thenThrowsIllegalArgumentException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompany(company.getName(), company.getFounderID(), VALID_EMAIL);

        // Act & Assert - should fail due to missing permission
        assertThrows(IllegalArgumentException.class, () ->
                productionCompanyService.setCompanyDiscountPolicy(token, company.getName(), DISCOUNT_COMPOSITE_SUM));

        // Assert - internal state remains unchanged
        ProductionCompany storedCompany = companyRepo.findById(company.getName());
        assertNotEquals(DISCOUNT_COMPOSITE_SUM, storedCompany.getDiscountPolicy());
    }

}
