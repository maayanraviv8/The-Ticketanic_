package acceptance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import application.dto.OrderDTO;
import application.dto.SuspensionDTO;
import application.service.AdminService;
import domain.Aggregates.event.Event;
import domain.Aggregates.member.Member;
import domain.Aggregates.member.Suspension;
import domain.Aggregates.order.Order;
import domain.Aggregates.productioncompany.FounderRole;
import domain.Aggregates.productioncompany.ProductionCompany;
import domain.Aggregates.show.Show;
import domain.Aggregates.ticket.Ticket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import domain.Aggregates.admin.Admin;
import domain.Aggregates.member.Member;
import domain.Aggregates.productioncompany.ProductionCompany;
import domain.exceptions.MemberException;
import repository.memoryRepo.AdminMemoryRepo;

public class AdminServiceTest extends AcceptanceTestBase {

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(orderRepo, adminRepo, memberRepo, eventRepo, companyRepo, authService, notificationService);
    }

    ///////// UC26 - Close Production Company /////////

    @Test
    void givenNullToken_whenCloseCompany_thenThrows() {
        // Arrange
        // (nothing)

        // Act
        assertThrows(Exception.class, () -> adminService.closeProductionCompany(null, VALID_COMPANY_NAME));

        // Assert - internal state
        assertEquals(0, memberRepo.getAll().size());
        assertEquals(0, adminRepo.getAll().size());
        assertEquals(0, companyRepo.getAll().size());
        assertEquals(0, eventRepo.getAll().size());
        assertEquals(0, notificationRepo.getAll().size());
        assertEquals(0, orderRepo.getAll().size());
        assertEquals(0, activeOrderRepo.getAll().size());
    }

    @Test
    void givenNonAdmin_whenCloseCompany_thenThrows() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        // Act
        assertThrows(Exception.class, () -> adminService.closeProductionCompany(token, VALID_COMPANY_NAME));

        // Assert - internal state
        assertEquals(1, memberRepo.getAll().size());
        assertEquals(0, adminRepo.getAll().size());
        assertEquals(0, companyRepo.getAll().size());
        assertEquals(0, notificationRepo.getAll().size());
    }

    @Test
    void givenMissingCompany_whenCloseCompany_thenThrows() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);

        // Act
        assertThrows(Exception.class, () -> adminService.closeProductionCompany(token, NON_EXISTENT_COMPANY_NAME));

        // Assert - internal state
        assertEquals(0, companyRepo.getAll().size());
        assertEquals(1, adminRepo.getAll().size());
        assertEquals(0, notificationRepo.getAll().size());
    }

    @Test
    void givenInactiveCompany_whenCloseCompany_thenThrows() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        company.setInactive();
        companyRepo.store(company);

        // Act
        assertThrows(Exception.class, () -> adminService.closeProductionCompany(token, VALID_COMPANY_NAME));

        // Assert - internal state
        assertFalse(companyRepo.findById(VALID_COMPANY_NAME).isActive());
        assertEquals(1, companyRepo.findById(VALID_COMPANY_NAME).getRoles().size());
        assertEquals(0, notificationRepo.getAll().size());
    }

    @Test
    void givenOnlyFounder_whenCloseCompany_thenClosedAndFounderNotified() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);

        // Act
        adminService.closeProductionCompany(token, VALID_COMPANY_NAME);

        // Assert - internal state
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertFalse(company.isActive());
        assertEquals(1, company.getRoles().size());
        assertTrue(company.getRoles().containsKey(VALID_EMAIL_2));
        assertInstanceOf(FounderRole.class, company.getRoles().get(VALID_EMAIL_2));
        assertEquals(1, notificationRepo.getAll().size());
        assertEquals(1, notificationRepo.findByRecipientEmail(VALID_EMAIL_2).size());
    }

    @Test
    void givenFounderAndOneOwner_whenCloseCompany_thenOwnerRevokedBothNotified() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeMember(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD);
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL_2, VALID_EMAIL_3);

        // Act
        adminService.closeProductionCompany(token, VALID_COMPANY_NAME);

        // Assert - internal state
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertFalse(company.isActive());
        assertEquals(1, company.getRoles().size());
        assertFalse(company.getRoles().containsKey(VALID_EMAIL_3));
        assertInstanceOf(FounderRole.class, company.getRoles().get(VALID_EMAIL_2));
        assertEquals(2, notificationRepo.getAll().size());
        assertEquals(1, notificationRepo.findByRecipientEmail(VALID_EMAIL_2).size());
        assertEquals(1, notificationRepo.findByRecipientEmail(VALID_EMAIL_3).size());
    }

    @Test
    void givenFounderAndOneManager_whenCloseCompany_thenManagerRevokedBothNotified() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeMember(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD);
        storeManagerInCompany(VALID_COMPANY_NAME, VALID_EMAIL_2, VALID_EMAIL_3);

        // Act
        adminService.closeProductionCompany(token, VALID_COMPANY_NAME);

        // Assert - internal state
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertFalse(company.isActive());
        assertEquals(1, company.getRoles().size());
        assertFalse(company.getRoles().containsKey(VALID_EMAIL_3));
        assertInstanceOf(FounderRole.class, company.getRoles().get(VALID_EMAIL_2));
        assertEquals(2, notificationRepo.getAll().size());
        assertEquals(1, notificationRepo.findByRecipientEmail(VALID_EMAIL_2).size());
        assertEquals(1, notificationRepo.findByRecipientEmail(VALID_EMAIL_3).size());
    }

    @Test
    void givenMultipleOwnersAndManagers_whenCloseCompany_thenOnlyFounderRemainsAllNotified() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeMember(OWNER1_USERNAME, OWNER1_EMAIL, VALID_PASSWORD);
        storeMember(OWNER2_USERNAME, OWNER2_EMAIL, VALID_PASSWORD);
        storeMember(MANAGER1_USERNAME, MANAGER1_EMAIL, VALID_PASSWORD);
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL_2, OWNER1_EMAIL);
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL_2, OWNER2_EMAIL);
        storeManagerInCompany(VALID_COMPANY_NAME, VALID_EMAIL_2, MANAGER1_EMAIL);

        // Act
        adminService.closeProductionCompany(token, VALID_COMPANY_NAME);

        // Assert - internal state
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertFalse(company.isActive());
        assertEquals(1, company.getRoles().size());
        assertInstanceOf(FounderRole.class, company.getRoles().get(VALID_EMAIL_2));
        assertFalse(company.getRoles().containsKey(OWNER1_EMAIL));
        assertFalse(company.getRoles().containsKey(OWNER2_EMAIL));
        assertFalse(company.getRoles().containsKey(MANAGER1_EMAIL));
        assertEquals(4, notificationRepo.getAll().size());
        assertEquals(1, notificationRepo.findByRecipientEmail(VALID_EMAIL_2).size());
        assertEquals(1, notificationRepo.findByRecipientEmail(OWNER1_EMAIL).size());
        assertEquals(1, notificationRepo.findByRecipientEmail(OWNER2_EMAIL).size());
        assertEquals(1, notificationRepo.findByRecipientEmail(MANAGER1_EMAIL).size());
    }

    @Test
    void givenTwoActiveCompanies_whenCloseOne_thenOtherUntouched() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // founder of companyA
        storeMember(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD); // founder of companyB
        storeCompany(VALID_COMPANY_NAME_2, VALID_EMAIL_2);
        storeCompany(VALID_COMPANY_NAME_3, VALID_EMAIL_3);
        storeMember(OWNER_A_USERNAME, OWNER_A_EMAIL, VALID_PASSWORD);
        storeMember(MANAGER_B_USERNAME, MANAGER_B_EMAIL, VALID_PASSWORD);
        storeOwnerInCompany(VALID_COMPANY_NAME_2, VALID_EMAIL_2, OWNER_A_EMAIL);
        storeManagerInCompany(VALID_COMPANY_NAME_3, VALID_EMAIL_3, MANAGER_B_EMAIL);

        // Act
        adminService.closeProductionCompany(token, VALID_COMPANY_NAME_2);

        // Assert - internal state
        assertFalse(companyRepo.findById(VALID_COMPANY_NAME_2).isActive());
        assertEquals(1, companyRepo.findById(VALID_COMPANY_NAME_2).getRoles().size());
        assertTrue(companyRepo.findById(VALID_COMPANY_NAME_3).isActive());
        assertEquals(2, companyRepo.findById(VALID_COMPANY_NAME_3).getRoles().size());
        assertTrue(companyRepo.findById(VALID_COMPANY_NAME_3).getRoles().containsKey(MANAGER_B_EMAIL));
        assertEquals(2, notificationRepo.getAll().size());
        assertTrue(notificationRepo.findByRecipientEmail(VALID_EMAIL_3).isEmpty());
        assertTrue(notificationRepo.findByRecipientEmail(MANAGER_B_EMAIL).isEmpty());
    }

    @Test
    void givenCompanyWithRoles_whenCloseCompany_thenAllNotificationsAreClosureType() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeMember(OWNER1_USERNAME, OWNER1_EMAIL, VALID_PASSWORD);
        storeMember(MANAGER1_USERNAME, MANAGER1_EMAIL, VALID_PASSWORD);
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL_2, OWNER1_EMAIL);
        storeManagerInCompany(VALID_COMPANY_NAME, VALID_EMAIL_2, MANAGER1_EMAIL);

        // Act
        adminService.closeProductionCompany(token, VALID_COMPANY_NAME);

        // Assert - internal state
        assertEquals(3, notificationRepo.getAll().size());
        assertTrue(notificationRepo.getAll().stream().noneMatch(n -> n instanceof domain.Aggregates.notification.AppointmentNotification));
    }

    @Test
    void givenCompanyWithRoles_whenCloseCompany_thenMembersStayActive() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeMember(OWNER1_USERNAME, OWNER1_EMAIL, VALID_PASSWORD);
        storeMember(MANAGER1_USERNAME, MANAGER1_EMAIL, VALID_PASSWORD);
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL_2, OWNER1_EMAIL);
        storeManagerInCompany(VALID_COMPANY_NAME, VALID_EMAIL_2, MANAGER1_EMAIL);

        // Act
        adminService.closeProductionCompany(token, VALID_COMPANY_NAME);

        // Assert - internal state
        assertTrue(memberRepo.findByEmail(VALID_EMAIL_2).isActive());
        assertTrue(memberRepo.findByEmail(OWNER1_EMAIL).isActive());
        assertTrue(memberRepo.findByEmail(MANAGER1_EMAIL).isActive());
        assertEquals(4, memberRepo.getAll().size());
    }

    @Test
    void givenAlreadyClosedSequentially_whenCloseAgain_thenNoExtraSideEffects() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeMember(OWNER1_USERNAME, OWNER1_EMAIL, VALID_PASSWORD);
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL_2, OWNER1_EMAIL);
        adminService.closeProductionCompany(token, VALID_COMPANY_NAME);

        int notificationsAfterFirst = notificationRepo.getAll().size();
        int rolesAfterFirst = companyRepo.findById(VALID_COMPANY_NAME).getRoles().size();
        boolean activeAfterFirst = companyRepo.findById(VALID_COMPANY_NAME).isActive();
        int founderNotifsAfterFirst = notificationRepo.findByRecipientEmail(VALID_EMAIL_2).size();
        int ownerNotifsAfterFirst = notificationRepo.findByRecipientEmail(OWNER1_EMAIL).size();

        // Act
        assertThrows(Exception.class, () -> adminService.closeProductionCompany(token, VALID_COMPANY_NAME));

        // Assert - internal state unchanged after failed second close
        assertEquals(notificationsAfterFirst, notificationRepo.getAll().size());
        assertEquals(rolesAfterFirst, companyRepo.findById(VALID_COMPANY_NAME).getRoles().size());
        assertEquals(activeAfterFirst, companyRepo.findById(VALID_COMPANY_NAME).isActive());
        assertEquals(founderNotifsAfterFirst, notificationRepo.findByRecipientEmail(VALID_EMAIL_2).size());
        assertEquals(ownerNotifsAfterFirst, notificationRepo.findByRecipientEmail(OWNER1_EMAIL).size());
    }

    ///////// Race Condition Tests /////////

    @Test
    void givenTwoAdminsCloseSameCompanyConcurrently_thenOnlyOneSucceeds() throws InterruptedException {
        // Arrange
        String token1 = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        String token2 = storeMemberAndLogin(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL_2);
        storeMember(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_3);
        storeMember(OWNER1_USERNAME, OWNER1_EMAIL, VALID_PASSWORD);
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL_3, OWNER1_EMAIL);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                adminService.closeProductionCompany(token1, VALID_COMPANY_NAME);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });
        executor.submit(() -> {
            try {
                startLatch.await();
                adminService.closeProductionCompany(token2, VALID_COMPANY_NAME);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        // Act
        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertTrue(completed, "Test timed out");
        assertEquals(1, successCount.get());
        assertEquals(1, failureCount.get());
        assertFalse(companyRepo.findById(VALID_COMPANY_NAME).isActive());
        assertEquals(1, companyRepo.findById(VALID_COMPANY_NAME).getRoles().size());
        assertEquals(2, notificationRepo.getAll().size());
        assertEquals(1, notificationRepo.findByRecipientEmail(VALID_EMAIL_3).size());
        assertEquals(1, notificationRepo.findByRecipientEmail(OWNER1_EMAIL).size());
    }


    ///////// UC27 - Cancel Platform membership /////////

    @Test
    void GivenMemberIsFounderOfActiveCompany_WhenCancelMembership_ThenThrowsAndStateUnchanged() {
        String adminEmail = "admin@mail.com";
        adminRepo = new AdminMemoryRepo() {
            @Override
            public Admin findById(String id) { return new Admin(); }
        };
        AdminService adminService = new AdminService(orderRepo, adminRepo, memberRepo, eventRepo, companyRepo, authService, notificationService);
        String adminToken = authService.generateToken(adminEmail);

        // Arrange - member who founds an active company
        String founderToken = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, founderToken);

        // Act + Assert - cancellation throws
        MemberException ex = assertThrows(MemberException.class,
                () -> adminService.cancelMembership(adminToken, VALID_EMAIL));
        assertTrue(ex.getMessage().contains("founder of active company"),
                "Exception message should explain the reason");

        // Assert - member state unchanged
        Member member = memberRepo.findByEmail(VALID_EMAIL);
        assertNotNull(member);
        assertTrue(member.isActive(), "Member should remain active after blocked cancellation");

        // Assert - company state unchanged
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        assertNotNull(company);
        assertTrue(company.isActive(), "Company should remain active");
        assertTrue(company.memberIsFounder(VALID_EMAIL), "Member should still be founder");
    }

    ///////// UC28 - View Global Purchase History /////////

    @Test
    void givenNullToken_whenGetByBuyers_thenThrowsAndNoSideEffects() {
        // Arrange
        // (nothing)

        // Act
        assertThrows(Exception.class, () -> adminService.getGlobalHistoryByBuyers(null, null, null));

        // Assert - internal state
        assertEquals(0, memberRepo.getAll().size());
        assertEquals(0, adminRepo.getAll().size());
        assertEquals(0, orderRepo.getAll().size());
        assertEquals(0, activeOrderRepo.getAll().size());
        assertEquals(0, eventRepo.getAll().size());
        assertEquals(0, companyRepo.getAll().size());
        assertEquals(0, notificationRepo.getAll().size());
    }

    @Test
    void givenNullToken_whenGetByCompaniesAndEvents_thenThrowsAndNoSideEffects() {
        // Arrange
        // (nothing)

        // Act
        assertThrows(Exception.class, () -> adminService.getGlobalHistoryByCompaniesAndEvents(null, null, null));

        // Assert - internal state
        assertEquals(0, memberRepo.getAll().size());
        assertEquals(0, adminRepo.getAll().size());
        assertEquals(0, orderRepo.getAll().size());
        assertEquals(0, activeOrderRepo.getAll().size());
        assertEquals(0, eventRepo.getAll().size());
        assertEquals(0, companyRepo.getAll().size());
        assertEquals(0, notificationRepo.getAll().size());
    }

    @Test
    void givenNonAdminMember_whenGetByBuyers_thenThrowsAndNoSideEffects() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        // Act
        assertThrows(Exception.class, () -> adminService.getGlobalHistoryByBuyers(token, null, null));

        // Assert - internal state
        assertEquals(1, memberRepo.getAll().size());
        assertEquals(0, adminRepo.getAll().size());
        assertEquals(0, orderRepo.getAll().size());
        assertEquals(0, notificationRepo.getAll().size());
    }

    @Test
    void givenNonAdminMember_whenGetByCompaniesAndEvents_thenThrowsAndNoSideEffects() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        // Act
        assertThrows(Exception.class, () -> adminService.getGlobalHistoryByCompaniesAndEvents(token, null, null));

        // Assert - internal state
        assertEquals(1, memberRepo.getAll().size());
        assertEquals(0, adminRepo.getAll().size());
        assertEquals(0, orderRepo.getAll().size());
        assertEquals(0, notificationRepo.getAll().size());
    }

    @Test
    void givenAdminAndEmptySystem_whenGetByBuyers_thenReturnsEmptyMap() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);

        // Act
        Map<String, List<OrderDTO>> result = adminService.getGlobalHistoryByBuyers(token, null, null);

        // Assert - return
        assertTrue(result.isEmpty());

        // Assert - internal state
        assertEquals(1, adminRepo.getAll().size());
        assertEquals(1, memberRepo.getAll().size());
        assertEquals(0, orderRepo.getAll().size());
        assertEquals(0, activeOrderRepo.getAll().size());
        assertEquals(0, eventRepo.getAll().size());
        assertEquals(0, companyRepo.getAll().size());
    }

    @Test
    void givenSingleBuyerWithSingleOrder_whenGetByBuyers_thenReturnsOneEntryWithOneOrder() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, 100.0, LocalDateTime.now());

        // Act
        Map<String, List<OrderDTO>> result = adminService.getGlobalHistoryByBuyers(token, null, null);

        // Assert - return
        assertEquals(1, result.size());
        assertTrue(result.containsKey(VALID_EMAIL_2));
        assertEquals(1, result.get(VALID_EMAIL_2).size());
        assertEquals(ORDER_ID_1, result.get(VALID_EMAIL_2).get(0).getOrderId());

        // Assert - internal state
        assertEquals(1, orderRepo.getAll().size());
        assertEquals(1, activeOrderRepo.getAll().size());
    }

    @Test
    void givenSingleBuyerWithMultipleOrders_whenGetByBuyers_thenReturnsAllOrders() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, 100.0, LocalDateTime.now());
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_2, ORDER_ID_2, event.getEventId(), VALID_SHOW_ID, 200.0, LocalDateTime.now());

        // Act
        Map<String, List<OrderDTO>> result = adminService.getGlobalHistoryByBuyers(token, null, null);

        // Assert - return
        assertEquals(2, result.get(VALID_EMAIL_2).size());
        assertTrue(result.get(VALID_EMAIL_2).stream().anyMatch(dto -> dto.getOrderId().equals(ORDER_ID_1)));
        assertTrue(result.get(VALID_EMAIL_2).stream().anyMatch(dto -> dto.getOrderId().equals(ORDER_ID_2)));
    }

    @Test
    void givenMultipleBuyersWithDifferentOrders_whenGetByBuyers_thenReturnsDistinctEntries() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeMember(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, 100.0, LocalDateTime.now());
        storeFullPurchaseChain(VALID_EMAIL_3, ACTIVE_ORDER_ID_2, ORDER_ID_2, event.getEventId(), VALID_SHOW_ID, 200.0, LocalDateTime.now());

        // Act
        Map<String, List<OrderDTO>> result = adminService.getGlobalHistoryByBuyers(token, null, null);

        // Assert - return
        assertEquals(2, result.size());
        assertEquals(1, result.get(VALID_EMAIL_2).size());
        assertEquals(ORDER_ID_1, result.get(VALID_EMAIL_2).get(0).getOrderId());
        assertEquals(1, result.get(VALID_EMAIL_3).size());
        assertEquals(ORDER_ID_2, result.get(VALID_EMAIL_3).get(0).getOrderId());
    }

    @Test
    void givenMemberWithoutOrders_whenGetByBuyers_thenMemberNotInResultMap() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeMember(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        storeFullPurchaseChain(VALID_EMAIL_3, ACTIVE_ORDER_ID_1, ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, 100.0, LocalDateTime.now());

        // Act
        Map<String, List<OrderDTO>> result = adminService.getGlobalHistoryByBuyers(token, null, null);

        // Assert - return
        assertEquals(1, result.size());
        assertTrue(result.containsKey(VALID_EMAIL_3));
        assertFalse(result.containsKey(VALID_EMAIL_2));
    }

    @Test
    void givenStartAndEndDateFilter_whenGetByBuyers_thenOnlyOrdersInRangeReturned() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        LocalDateTime before = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime inside = LocalDateTime.of(2024, 6, 15, 0, 0);
        LocalDateTime after  = LocalDateTime.of(2024, 12, 31, 0, 0);
        LocalDateTime start  = LocalDateTime.of(2024, 3, 1, 0, 0);
        LocalDateTime end    = LocalDateTime.of(2024, 9, 1, 0, 0);
        storeFullPurchaseChain(VALID_EMAIL_2, "activeOrder-before", "order-before", event.getEventId(), VALID_SHOW_ID, 50.0, before);
        storeFullPurchaseChain(VALID_EMAIL_2, "activeOrder-inside", ORDER_ID_1,     event.getEventId(), VALID_SHOW_ID, 100.0, inside);
        storeFullPurchaseChain(VALID_EMAIL_2, "activeOrder-after",  "order-after",  event.getEventId(), VALID_SHOW_ID, 150.0, after);

        // Act
        Map<String, List<OrderDTO>> result = adminService.getGlobalHistoryByBuyers(token, start, end);

        // Assert - return
        assertEquals(1, result.get(VALID_EMAIL_2).size());
        assertEquals(ORDER_ID_1, result.get(VALID_EMAIL_2).get(0).getOrderId());
    }

    @Test
    void givenOrdersOnExactBoundaryDates_whenGetByBuyers_thenBoundariesIncluded() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        LocalDateTime start = LocalDateTime.of(2024, 6, 1, 0, 0);
        LocalDateTime end   = LocalDateTime.of(2024, 6, 30, 0, 0);
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, 100.0, start);
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_2, ORDER_ID_2, event.getEventId(), VALID_SHOW_ID, 200.0, end);

        // Act
        Map<String, List<OrderDTO>> result = adminService.getGlobalHistoryByBuyers(token, start, end);

        // Assert - return
        assertEquals(2, result.get(VALID_EMAIL_2).size());
        assertTrue(result.get(VALID_EMAIL_2).stream().anyMatch(dto -> dto.getOrderId().equals(ORDER_ID_1)));
        assertTrue(result.get(VALID_EMAIL_2).stream().anyMatch(dto -> dto.getOrderId().equals(ORDER_ID_2)));
    }

    @Test
    void givenOrphanOrderIdInMember_whenGetByBuyers_thenSkippedDefensivelyAndNoCrash() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, 100.0, LocalDateTime.now());
        linkOrderToMember(VALID_EMAIL_2, "ghost-order-id");

        // Act
        Map<String, List<OrderDTO>> result = adminService.getGlobalHistoryByBuyers(token, null, null);

        // Assert - return
        assertEquals(1, result.get(VALID_EMAIL_2).size());
        assertEquals(ORDER_ID_1, result.get(VALID_EMAIL_2).get(0).getOrderId());

        // Assert - internal state
        assertEquals(1, orderRepo.getAll().size());
    }

    @Test
    void givenAdminAndEmptySystem_whenGetByCompaniesAndEvents_thenReturnsEmptyMap() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);

        // Act
        Map<String, Map<String, List<OrderDTO>>> result = adminService.getGlobalHistoryByCompaniesAndEvents(token, null, null);

        // Assert - return
        assertTrue(result.isEmpty());
    }

    @Test
    void givenSingleCompanyWithOneEventAndOneOrder_whenGetByCompaniesAndEvents_thenReturnsNestedMap() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        String eventId = event.getEventId();
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1, eventId, VALID_SHOW_ID, 100.0, LocalDateTime.now());

        // Act
        Map<String, Map<String, List<OrderDTO>>> result = adminService.getGlobalHistoryByCompaniesAndEvents(token, null, null);

        // Assert - return
        assertEquals(1, result.size());
        assertNotNull(result.get(VALID_COMPANY_NAME));
        assertEquals(1, result.get(VALID_COMPANY_NAME).size());
        assertEquals(1, result.get(VALID_COMPANY_NAME).get(eventId).size());
        assertEquals(ORDER_ID_1, result.get(VALID_COMPANY_NAME).get(eventId).get(0).getOrderId());
    }

    @Test
    void givenSingleCompanyWithMultipleEvents_whenGetByCompaniesAndEvents_thenEachEventListedSeparately() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event1 = storeEvent(VALID_COMPANY_NAME, true);
        Event event2 = storeEvent(VALID_COMPANY_NAME, true);
        String eventId1 = event1.getEventId();
        String eventId2 = event2.getEventId();
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1, eventId1, VALID_SHOW_ID, 100.0, LocalDateTime.now());
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_2, ORDER_ID_2, eventId2, VALID_SHOW_ID, 200.0, LocalDateTime.now());

        // Act
        Map<String, Map<String, List<OrderDTO>>> result = adminService.getGlobalHistoryByCompaniesAndEvents(token, null, null);

        // Assert - return
        assertEquals(1, result.size());
        assertEquals(2, result.get(VALID_COMPANY_NAME).size());
        assertEquals(1, result.get(VALID_COMPANY_NAME).get(eventId1).size());
        assertEquals(ORDER_ID_1, result.get(VALID_COMPANY_NAME).get(eventId1).get(0).getOrderId());
        assertEquals(1, result.get(VALID_COMPANY_NAME).get(eventId2).size());
        assertEquals(ORDER_ID_2, result.get(VALID_COMPANY_NAME).get(eventId2).get(0).getOrderId());
    }

    @Test
    void givenMultipleCompanies_whenGetByCompaniesAndEvents_thenEachCompanyListedSeparately() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        String companyA = "companyA";
        String companyB = "companyB";
        storeCompany(companyA, VALID_EMAIL);
        storeCompany(companyB, VALID_EMAIL);
        Event eventA = storeEvent(companyA, true);
        Event eventB = storeEvent(companyB, true);
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1, eventA.getEventId(), VALID_SHOW_ID, 100.0, LocalDateTime.now());
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_2, ORDER_ID_2, eventB.getEventId(), VALID_SHOW_ID, 200.0, LocalDateTime.now());

        // Act
        Map<String, Map<String, List<OrderDTO>>> result = adminService.getGlobalHistoryByCompaniesAndEvents(token, null, null);

        // Assert - return
        assertEquals(2, result.size());
        assertNotNull(result.get(companyA));
        assertEquals(1, result.get(companyA).get(eventA.getEventId()).size());
        assertEquals(ORDER_ID_1, result.get(companyA).get(eventA.getEventId()).get(0).getOrderId());
        assertNotNull(result.get(companyB));
        assertEquals(1, result.get(companyB).get(eventB.getEventId()).size());
        assertEquals(ORDER_ID_2, result.get(companyB).get(eventB.getEventId()).get(0).getOrderId());
    }

    @Test
    void givenSameEventTwoOrdersFromSameBuyer_whenGetByCompaniesAndEvents_thenBothListed() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        String eventId = event.getEventId();
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1, eventId, VALID_SHOW_ID, 100.0, LocalDateTime.now());
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_2, ORDER_ID_2, eventId, VALID_SHOW_ID, 200.0, LocalDateTime.now());

        // Act
        Map<String, Map<String, List<OrderDTO>>> result = adminService.getGlobalHistoryByCompaniesAndEvents(token, null, null);

        // Assert - return
        assertEquals(2, result.get(VALID_COMPANY_NAME).get(eventId).size());
    }

    @Test
    void givenStartAndEndDateFilter_whenGetByCompaniesAndEvents_thenOnlyOrdersInRangeReturned() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        String eventId = event.getEventId();
        LocalDateTime before = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime inside = LocalDateTime.of(2024, 6, 15, 0, 0);
        LocalDateTime after  = LocalDateTime.of(2024, 12, 31, 0, 0);
        LocalDateTime start  = LocalDateTime.of(2024, 3, 1, 0, 0);
        LocalDateTime end    = LocalDateTime.of(2024, 9, 1, 0, 0);
        storeFullPurchaseChain(VALID_EMAIL_2, "activeOrder-before", "order-before", eventId, VALID_SHOW_ID, 50.0,  before);
        storeFullPurchaseChain(VALID_EMAIL_2, "activeOrder-inside", ORDER_ID_1,     eventId, VALID_SHOW_ID, 100.0, inside);
        storeFullPurchaseChain(VALID_EMAIL_2, "activeOrder-after",  "order-after",  eventId, VALID_SHOW_ID, 150.0, after);

        // Act
        Map<String, Map<String, List<OrderDTO>>> result = adminService.getGlobalHistoryByCompaniesAndEvents(token, start, end);

        // Assert - return
        assertEquals(1, result.get(VALID_COMPANY_NAME).get(eventId).size());
        assertEquals(ORDER_ID_1, result.get(VALID_COMPANY_NAME).get(eventId).get(0).getOrderId());
    }

    @Test
    void givenFilterMatchesNoOrders_whenGetByCompaniesAndEvents_thenReturnsEmptyMap() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        LocalDateTime purchaseTime = LocalDateTime.of(2023, 6, 15, 0, 0);
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, 100.0, purchaseTime);
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end   = LocalDateTime.of(2024, 12, 31, 0, 0);

        // Act
        Map<String, Map<String, List<OrderDTO>>> result = adminService.getGlobalHistoryByCompaniesAndEvents(token, start, end);

        // Assert - return
        assertTrue(result.isEmpty());

        // Assert - internal state
        assertEquals(1, orderRepo.getAll().size());
    }

    @Test
    void givenOrderWithMissingEvent_whenGetByCompaniesAndEvents_thenSkippedDefensively() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        // Build orphan: Order whose eventId does not exist in eventRepo
        Order orphanOrder = new Order(ORDER_ID_1, "ghost-event-id", VALID_SHOW_ID, java.util.List.of(), 100.0, LocalDateTime.now(), null);
        orderRepo.store(orphanOrder);
        linkOrderToMember(VALID_EMAIL_2, ORDER_ID_1);

        // Act
        Map<String, Map<String, List<OrderDTO>>> result = adminService.getGlobalHistoryByCompaniesAndEvents(token, null, null);

        // Assert - return
        assertTrue(result.isEmpty());

        // Assert - internal state
        assertEquals(1, orderRepo.getAll().size());
    }

    @Test
    void givenCanceledBuyerMember_whenGetByBuyers_thenOrderHistoryStillAppears() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, 100.0, LocalDateTime.now());
        Member buyer = memberRepo.findByEmail(VALID_EMAIL_2);
        buyer.setActive(false);
        memberRepo.store(buyer);

        // Act
        Map<String, List<OrderDTO>> result = adminService.getGlobalHistoryByBuyers(token, null, null);

        // Assert - return value
        assertEquals(1, result.size());
        assertTrue(result.containsKey(VALID_EMAIL_2));
        assertEquals(1, result.get(VALID_EMAIL_2).size());
        assertEquals(ORDER_ID_1, result.get(VALID_EMAIL_2).get(0).getOrderId());

        // Assert - internal state
        assertEquals(2, memberRepo.getAll().size());
        assertFalse(memberRepo.findByEmail(VALID_EMAIL_2).isActive());
        assertEquals(1, orderRepo.getAll().size());
        assertEquals(100.0, orderRepo.findById(ORDER_ID_1).getPrice());
        assertEquals(1, activeOrderRepo.getAll().size());
        assertEquals(0, notificationRepo.getAll().size());
    }

    @Test
    void givenClosedCompany_whenGetByCompaniesAndEvents_thenOrderHistoryStillAppears() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, 100.0, LocalDateTime.now());
        ProductionCompany company = companyRepo.findById(VALID_COMPANY_NAME);
        company.setActive(false);
        companyRepo.store(company);

        // Act
        Map<String, Map<String, List<OrderDTO>>> result = adminService.getGlobalHistoryByCompaniesAndEvents(token, null, null);

        // Assert - return value
        assertEquals(1, result.size());
        assertTrue(result.containsKey(VALID_COMPANY_NAME));
        assertEquals(1, result.get(VALID_COMPANY_NAME).size());
        assertEquals(1, result.get(VALID_COMPANY_NAME).get(event.getEventId()).size());
        assertEquals(ORDER_ID_1, result.get(VALID_COMPANY_NAME).get(event.getEventId()).get(0).getOrderId());

        // Assert - internal state
        assertEquals(1, companyRepo.getAll().size());
        assertFalse(companyRepo.findById(VALID_COMPANY_NAME).isActive());
        assertEquals(1, eventRepo.getAll().size());
        assertEquals(1, orderRepo.getAll().size());
        assertEquals(1, activeOrderRepo.getAll().size());
    }

    @Test
    void givenInactiveShowAfterPurchase_whenGetByCompaniesAndEvents_thenHistoryUnaffected() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true);
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1, event.getEventId(), show.getShowId(), 100.0, LocalDateTime.now());
        show.setActive(false);
        showRepo.store(show);

        // Act
        Map<String, Map<String, List<OrderDTO>>> result = adminService.getGlobalHistoryByCompaniesAndEvents(token, null, null);

        // Assert - return value
        assertEquals(1, result.size());
        assertTrue(result.containsKey(VALID_COMPANY_NAME));
        assertEquals(1, result.get(VALID_COMPANY_NAME).get(event.getEventId()).size());
        assertEquals(ORDER_ID_1, result.get(VALID_COMPANY_NAME).get(event.getEventId()).get(0).getOrderId());

        // Assert - internal state
        assertEquals(1, showRepo.getAll().size());
        assertFalse(showRepo.findById(show.getShowId()).isActive());
        assertEquals(1, orderRepo.getAll().size());
        assertEquals(1, activeOrderRepo.getAll().size());
    }

    @Test
    void givenChangedTicketPriceAfterPurchase_whenGetByBuyers_thenOriginalPriceReturned() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        double originalPrice = 100.0;
        Ticket ticket = storeTicket(TICKET_ID_1, originalPrice);
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, originalPrice, LocalDateTime.now());
        ticket.setPrice(200.0);
        ticketRepo.store(ticket);

        // Act
        Map<String, List<OrderDTO>> result = adminService.getGlobalHistoryByBuyers(token, null, null);

        // Assert - return value
        assertEquals(originalPrice, result.get(VALID_EMAIL_2).get(0).getFinalPrice());

        // Assert - internal state
        assertEquals(100.0, orderRepo.findById(ORDER_ID_1).getPrice());
        assertEquals(200.0, ticketRepo.findById(TICKET_ID_1).getPrice());
    }

    @Test
    void givenSingleOrder_whenGetByBuyers_thenOrderDTOFieldsAreFullyPopulated() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        LocalDateTime fixedTime = LocalDateTime.of(2024, 6, 1, 12, 30);
        double finalPrice = 250.50;
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, finalPrice, fixedTime);

        // Act
        Map<String, List<OrderDTO>> result = adminService.getGlobalHistoryByBuyers(token, null, null);

        // Assert - return value
        OrderDTO dto = result.get(VALID_EMAIL_2).get(0);
        assertEquals(ORDER_ID_1, dto.getOrderId());
        assertEquals(event.getEventId(), dto.getEventId());
        assertEquals(VALID_SHOW_ID, dto.getShowId());
        assertEquals(java.util.List.of(), dto.getTicketIds());
        assertEquals(finalPrice, dto.getFinalPrice());
        assertEquals(fixedTime, dto.getPurchaseTime());

        // Assert - internal state
        assertEquals(1, orderRepo.getAll().size());
        assertEquals(1, activeOrderRepo.getAll().size());
    }

    @Test
    void givenSingleOrder_whenGetByCompaniesAndEvents_thenOrderDTOFieldsAreFullyPopulated() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        LocalDateTime fixedTime = LocalDateTime.of(2024, 6, 1, 12, 30);
        double finalPrice = 250.50;
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, finalPrice, fixedTime);

        // Act
        Map<String, Map<String, List<OrderDTO>>> result = adminService.getGlobalHistoryByCompaniesAndEvents(token, null, null);

        // Assert - return value
        OrderDTO dto = result.get(VALID_COMPANY_NAME).get(event.getEventId()).get(0);
        assertEquals(ORDER_ID_1, dto.getOrderId());
        assertEquals(event.getEventId(), dto.getEventId());
        assertEquals(VALID_SHOW_ID, dto.getShowId());
        assertEquals(java.util.List.of(), dto.getTicketIds());
        assertEquals(finalPrice, dto.getFinalPrice());
        assertEquals(fixedTime, dto.getPurchaseTime());

        // Assert - internal state
        assertEquals(1, orderRepo.getAll().size());
        assertEquals(1, activeOrderRepo.getAll().size());
    }

    @Test
    void givenSameDataSet_whenQueriedByBothSlices_thenSameOrdersAppearInBoth() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeMember(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, 100.0, LocalDateTime.now());
        storeFullPurchaseChain(VALID_EMAIL_3, ACTIVE_ORDER_ID_2, ORDER_ID_2, event.getEventId(), VALID_SHOW_ID, 200.0, LocalDateTime.now());

        // Act
        Map<String, List<OrderDTO>> byBuyers = adminService.getGlobalHistoryByBuyers(token, null, null);
        Map<String, Map<String, List<OrderDTO>>> byCompanies = adminService.getGlobalHistoryByCompaniesAndEvents(token, null, null);

        // Assert - return value
        long totalByBuyers = byBuyers.values().stream().mapToLong(List::size).sum();
        long totalByCompanies = byCompanies.values().stream()
                .flatMap(e -> e.values().stream())
                .mapToLong(List::size).sum();
        assertEquals(totalByBuyers, totalByCompanies);

        Set<String> orderIdsByBuyers = byBuyers.values().stream()
                .flatMap(List::stream)
                .map(OrderDTO::getOrderId)
                .collect(Collectors.toSet());
        Set<String> orderIdsByCompanies = byCompanies.values().stream()
                .flatMap(e -> e.values().stream())
                .flatMap(List::stream)
                .map(OrderDTO::getOrderId)
                .collect(Collectors.toSet());
        assertEquals(orderIdsByBuyers, orderIdsByCompanies);

        // Assert - internal state
        assertEquals(2, orderRepo.getAll().size());
        assertEquals(2, activeOrderRepo.getAll().size());
    }

    @Test
    void givenAdminWhoIsAlsoBuyer_whenGetByBuyers_thenAdminEntryAppearsInResult() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        storeFullPurchaseChain(VALID_EMAIL, ACTIVE_ORDER_ID_1, ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, 100.0, LocalDateTime.now());

        // Act
        Map<String, List<OrderDTO>> result = adminService.getGlobalHistoryByBuyers(token, null, null);

        // Assert - return value
        assertEquals(1, result.size());
        assertTrue(result.containsKey(VALID_EMAIL));
        assertEquals(1, result.get(VALID_EMAIL).size());
        assertEquals(ORDER_ID_1, result.get(VALID_EMAIL).get(0).getOrderId());

        // Assert - internal state
        assertEquals(1, adminRepo.getAll().size());
        assertEquals(1, memberRepo.getAll().size());
        assertEquals(1, orderRepo.getAll().size());
    }

    @Test
    void givenPopulatedSystem_whenAdminCallsBothQueries_thenNoStateMutated() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1, event.getEventId(), VALID_SHOW_ID, 100.0, LocalDateTime.now());

        int membersBefore         = memberRepo.getAll().size();
        int adminsBefore          = adminRepo.getAll().size();
        int ordersBefore          = orderRepo.getAll().size();
        int activeOrdersBefore    = activeOrderRepo.getAll().size();
        int eventsBefore          = eventRepo.getAll().size();
        int companiesBefore       = companyRepo.getAll().size();
        int notificationsBefore   = notificationRepo.getAll().size();
        boolean buyerActiveBefore   = memberRepo.findByEmail(VALID_EMAIL_2).isActive();
        int buyerOrderCountBefore   = memberRepo.findByEmail(VALID_EMAIL_2).getOrderIds().size();
        double orderPriceBefore     = orderRepo.findById(ORDER_ID_1).getPrice();
        boolean companyActiveBefore = companyRepo.findById(VALID_COMPANY_NAME).isActive();

        // Act
        adminService.getGlobalHistoryByBuyers(token, null, null);
        adminService.getGlobalHistoryByCompaniesAndEvents(token, null, null);

        // Assert - internal state
        assertEquals(membersBefore, memberRepo.getAll().size());
        assertEquals(adminsBefore, adminRepo.getAll().size());
        assertEquals(ordersBefore, orderRepo.getAll().size());
        assertEquals(activeOrdersBefore, activeOrderRepo.getAll().size());
        assertEquals(eventsBefore, eventRepo.getAll().size());
        assertEquals(companiesBefore, companyRepo.getAll().size());
        assertEquals(notificationsBefore, notificationRepo.getAll().size());
        assertEquals(buyerActiveBefore, memberRepo.findByEmail(VALID_EMAIL_2).isActive());
        assertEquals(buyerOrderCountBefore, memberRepo.findByEmail(VALID_EMAIL_2).getOrderIds().size());
        assertEquals(orderPriceBefore, orderRepo.findById(ORDER_ID_1).getPrice());
        assertEquals(companyActiveBefore, companyRepo.findById(VALID_COMPANY_NAME).isActive());
    }

    ///////// UC29 - Suspend Member /////////
     
     
    @Test
    void givenNullToken_whenSuspendMember_thenThrows() {
        // Arrange
        // (nothing)

        // Act + Assert
        assertThrows(Exception.class, () -> adminService.suspendMember(null, VALID_EMAIL));

        // Assert - internal state
        assertEquals(0, memberRepo.getAll().size());
    }

    @Test
    void givenNonAdminToken_whenSuspendMember_thenThrows() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        // Act + Assert
        assertThrows(Exception.class, () -> adminService.suspendMember(token, VALID_EMAIL_2));

        // Assert - internal state
        assertFalse(memberRepo.findByEmail(VALID_EMAIL).isSuspended());
    }

    @Test
    void givenAdminToken_whenSuspendNonExistentMember_thenThrowsMemberException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);

        // Act + Assert
        assertThrows(MemberException.class, () -> adminService.suspendMember(token, VALID_EMAIL_2), "Should throw MemberException if member is already suspended");

        // Assert - internal state
        assertEquals(1, memberRepo.getAll().size());
    }

    @Test
    void givenAdminToken_whenSuspendCancelledMember_thenThrowsMemberException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        Member target = memberRepo.findByEmail(VALID_EMAIL_2);
        target.cancelMembership();
        memberRepo.store(target);

        // Act + Assert
        assertThrows(MemberException.class, () -> adminService.suspendMember(token, VALID_EMAIL_2));

        // Assert - internal state
        assertFalse(memberRepo.findByEmail(VALID_EMAIL_2).isSuspended());
    }

    @Test
    void givenAdminToken_whenSuspendAnotherAdmin_thenThrowsMemberException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL_2);

        // Act + Assert
        assertThrows(MemberException.class, () -> adminService.suspendMember(token, VALID_EMAIL_2));

        // Assert - internal state
        assertFalse(memberRepo.findByEmail(VALID_EMAIL_2).isSuspended());
    }

    @Test
    void givenAdminToken_whenSuspendSelf_thenThrowsMemberException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);

        // Act + Assert
        assertThrows(MemberException.class, () -> adminService.suspendMember(token, VALID_EMAIL));

        // Assert - internal state
        assertFalse(memberRepo.findByEmail(VALID_EMAIL).isSuspended());
    }

    @Test
    void givenAdminToken_whenSuspendAlreadySuspendedMember_thenThrowsMemberException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        suspendMember(VALID_EMAIL_2);

        // Act + Assert
        assertThrows(MemberException.class, () -> adminService.suspendMember(token, VALID_EMAIL_2));

        // Assert - internal state
        assertEquals(1, memberRepo.findByEmail(VALID_EMAIL_2).getSuspensionHistory().size());
    }

    @Test
    void givenAdminAndActiveMember_whenSuspendMember_thenMemberIsSuspended() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);

        // Act
        adminService.suspendMember(token, VALID_EMAIL_2);

        // Assert
        Member member = memberRepo.findByEmail(VALID_EMAIL_2);
        assertTrue(member.isSuspended());
        assertEquals(1, member.getSuspensionHistory().size());
        assertTrue(member.getSuspensionHistory().get(0).isActive());
        assertNotNull(member.getSuspensionHistory().get(0).getSuspensionDate());
    }

    @Test
    void givenAdminAndActiveMember_whenSuspendMember_thenAccountNotCancelled() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);

        // Act
        adminService.suspendMember(token, VALID_EMAIL_2);

        // Assert
        Member member = memberRepo.findByEmail(VALID_EMAIL_2);
        assertTrue(member.isActive());
    }

    @Test
    void givenAdminAndActiveMember_whenSuspendMember_thenOtherMembersUnaffected() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeMember(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD);

        // Act
        adminService.suspendMember(token, VALID_EMAIL_2);

        // Assert
        assertFalse(memberRepo.findByEmail(VALID_EMAIL_3).isSuspended());
        assertTrue(memberRepo.findByEmail(VALID_EMAIL_3).getSuspensionHistory().isEmpty());
    }

    ///////// UC30 - Unsuspend Member /////////

    @Test
    void givenNullToken_whenUnsuspendMember_thenThrows() {
        // Arrange
        // (nothing)

        // Act + Assert
        assertThrows(Exception.class, () -> adminService.unsuspendMember(null, VALID_EMAIL));

        // Assert - internal state
        assertTrue(memberRepo.getAll().isEmpty());
    }

    @Test
    void givenNonAdminToken_whenUnsuspendMember_thenThrows() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        // Act + Assert
        assertThrows(Exception.class, () -> adminService.unsuspendMember(token, VALID_EMAIL_2));

        // Assert - internal state
        assertFalse(memberRepo.findByEmail(VALID_EMAIL).isSuspended());
    }

    @Test
    void givenAdminToken_whenUnsuspendNonExistentMember_thenThrowsMemberException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);

        // Act + Assert
        assertThrows(MemberException.class, () -> adminService.unsuspendMember(token, VALID_EMAIL_2));

        // Assert - internal state
        assertEquals(1, memberRepo.getAll().size());
    }

    @Test
    void givenAdminToken_whenUnsuspendActiveMemberWhoIsNotSuspended_thenThrowsMemberException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);

        // Act + Assert
        assertThrows(MemberException.class, () -> adminService.unsuspendMember(token, VALID_EMAIL_2));

        // Assert - internal state
        assertTrue(memberRepo.findByEmail(VALID_EMAIL_2).getSuspensionHistory().isEmpty());
    }

    @Test
    void givenAdminAndSuspendedMember_whenUnsuspend_thenIsSuspendedFalse() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        suspendMember(VALID_EMAIL_2);

        // Act
        adminService.unsuspendMember(token, VALID_EMAIL_2);

        // Assert
        assertFalse(memberRepo.findByEmail(VALID_EMAIL_2).isSuspended());
    }

    @Test
    void givenAdminAndSuspendedMember_whenUnsuspend_thenSuspensionEntryIsClosed() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        suspendMember(VALID_EMAIL_2);

        // Act
        adminService.unsuspendMember(token, VALID_EMAIL_2);

        // Assert
        Suspension entry = memberRepo.findByEmail(VALID_EMAIL_2).getSuspensionHistory().get(0);
        assertFalse(entry.isActive());
        assertNotNull(entry.getEndDate());
        assertFalse(entry.getEndDate().isBefore(entry.getSuspensionDate()));
    }

    @Test
    void givenAdminAndSuspendedMember_whenUnsuspend_thenHistorySizeUnchanged() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        suspendMember(VALID_EMAIL_2);

        // Act
        adminService.unsuspendMember(token, VALID_EMAIL_2);

        // Assert
        assertEquals(1, memberRepo.findByEmail(VALID_EMAIL_2).getSuspensionHistory().size());
    }

    @Test
    void givenAdminAndSuspendedMember_whenUnsuspend_thenIsActiveUnchanged() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        suspendMember(VALID_EMAIL_2);

        // Act
        adminService.unsuspendMember(token, VALID_EMAIL_2);

        // Assert
        assertTrue(memberRepo.findByEmail(VALID_EMAIL_2).isActive());
    }

    @Test
    void givenAdminAndSuspendedMember_whenUnsuspend_thenOtherMembersUnaffected() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeMember(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD);
        suspendMember(VALID_EMAIL_2);

        // Act
        adminService.unsuspendMember(token, VALID_EMAIL_2);

        // Assert
        assertFalse(memberRepo.findByEmail(VALID_EMAIL_3).isSuspended());
        assertTrue(memberRepo.findByEmail(VALID_EMAIL_3).getSuspensionHistory().isEmpty());
    }

    ///////// UC31 - View Suspensions /////////

    @Test
    void givenNullToken_whenGetSuspensions_thenThrows() {
        // Arrange
        // (nothing)

        // Act
        assertThrows(Exception.class, () -> adminService.getMemberSuspensions(null, false));

        // Assert - internal state
        assertEquals(0, memberRepo.getAll().size());
        assertEquals(0, adminRepo.getAll().size());
    }

    @Test
    void givenNonAdminToken_whenGetSuspensions_thenThrows() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        // Act
        assertThrows(Exception.class, () -> adminService.getMemberSuspensions(token, false));

        // Assert - internal state
        assertEquals(1, memberRepo.getAll().size());
        assertEquals(0, adminRepo.getAll().size());
    }

    @Test
    void givenAdminAndNoMembersAtAll_whenGetSuspensions_thenReturnsEmptyList() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);

        // Act
        List<SuspensionDTO> result = adminService.getMemberSuspensions(token, false);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void givenAdminAndMembersWithNoSuspensions_whenGetSuspensions_thenReturnsEmptyList() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeMember(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD);

        // Act
        List<SuspensionDTO> result = adminService.getMemberSuspensions(token, false);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void givenAdminAndOnlyEndedSuspensions_whenGetActiveOnly_thenReturnsEmptyList() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        suspendMember(VALID_EMAIL_2);
        adminService.unsuspendMember(token, VALID_EMAIL_2);

        // Act
        List<SuspensionDTO> result = adminService.getMemberSuspensions(token, true);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void givenOneSuspendedMember_whenGetSuspensions_thenReturnsOneRecord() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        suspendMember(VALID_EMAIL_2);

        // Act
        List<SuspensionDTO> result = adminService.getMemberSuspensions(token, false);

        // Assert
        assertEquals(1, result.size());
    }

    @Test
    void givenOneEndedSuspension_whenGetSuspensions_thenReturnsOneRecord() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        suspendMember(VALID_EMAIL_2);
        adminService.unsuspendMember(token, VALID_EMAIL_2);

        // Act
        List<SuspensionDTO> result = adminService.getMemberSuspensions(token, false);

        // Assert
        assertEquals(1, result.size());
    }

    @Test
    void givenTwoMembers_oneActiveSuspension_oneEndedSuspension_whenGetSuspensions_thenReturnsBoth() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeMember(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD);
        suspendMember(VALID_EMAIL_2);
        adminService.unsuspendMember(token, VALID_EMAIL_2);
        suspendMember(VALID_EMAIL_3);

        // Act
        List<SuspensionDTO> result = adminService.getMemberSuspensions(token, false);

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    void givenOneMemberWithTwoSuspensionCycles_whenGetSuspensions_thenReturnsTwoRecords() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        suspendMember(VALID_EMAIL_2);
        adminService.unsuspendMember(token, VALID_EMAIL_2);
        suspendMember(VALID_EMAIL_2);

        // Act
        List<SuspensionDTO> result = adminService.getMemberSuspensions(token, false);

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    void givenOneSuspendedMember_whenGetActiveOnlySuspensions_thenReturnsOneRecord() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        suspendMember(VALID_EMAIL_2);

        // Act
        List<SuspensionDTO> result = adminService.getMemberSuspensions(token, true);

        // Assert
        assertEquals(1, result.size());
    }

    @Test
    void givenTwoMembers_oneActive_oneEnded_whenGetActiveOnlySuspensions_thenReturnsOnlyActive() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeMember(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD);
        suspendMember(VALID_EMAIL_2);
        adminService.unsuspendMember(token, VALID_EMAIL_2);
        suspendMember(VALID_EMAIL_3);

        // Act
        List<SuspensionDTO> result = adminService.getMemberSuspensions(token, true);

        // Assert
        assertEquals(1, result.size());
        assertEquals(VALID_EMAIL_3, result.get(0).memberEmail);
        assertNull(result.get(0).suspensionEndDate);
    }

    @Test
    void givenTwoActiveSuspensions_whenGetActiveOnlySuspensions_thenReturnsBoth() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeMember(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD);
        suspendMember(VALID_EMAIL_2);
        suspendMember(VALID_EMAIL_3);

        // Act
        List<SuspensionDTO> result = adminService.getMemberSuspensions(token, true);

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    void givenActiveSuspension_whenGetSuspensions_thenDTOFieldsAreCorrect() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        suspendMember(VALID_EMAIL_2);

        // Act
        List<SuspensionDTO> result = adminService.getMemberSuspensions(token, false);

        // Assert
        SuspensionDTO dto = result.get(0);
        assertEquals(VALID_EMAIL_2, dto.memberEmail);
        assertNotNull(dto.suspensionDate);
        assertNull(dto.suspensionEndDate);
        assertNotNull(dto.duration);
        assertFalse(dto.duration.isNegative());
    }

    @Test
    void givenEndedSuspension_whenGetSuspensions_thenDTOEndDateIsSet() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeAdmin(VALID_EMAIL);
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        suspendMember(VALID_EMAIL_2);
        adminService.unsuspendMember(token, VALID_EMAIL_2);

        // Act
        List<SuspensionDTO> result = adminService.getMemberSuspensions(token, false);

        // Assert
        SuspensionDTO dto = result.get(0);
        assertNotNull(dto.suspensionEndDate);
        assertNotNull(dto.suspensionDate);
        assertFalse(dto.suspensionEndDate.isBefore(dto.suspensionDate));
        assertNotNull(dto.duration);
        assertFalse(dto.duration.isNegative());
    }

}
