package application.service;

import application.dto.*;
import application.factories.DiscountPolicyFactory;
import application.factories.PurchasePolicyFactory;
import application.interfaces.IAuth;
import application.interfaces.INotificationService;
import domain.Aggregates.event.Event;
import domain.Aggregates.productioncompany.RoleState;
import domain.Aggregates.productioncompany.Permissions;
import domain.Aggregates.productioncompany.ProductionCompany;
import domain.Irepository.*;
import domain.exceptions.CompanyNotFoundException;
import domain.exceptions.UnauthorizedException;
import domain.policies.discount.IDiscountRule;
import domain.policies.purchase.IPurchaseRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class ProductionCompanyService {

    private static final Logger logger = LoggerFactory.getLogger(ProductionCompanyService.class);
    private final IProductionCompanyRepository repository;
    private final IAuth authService;
    private final IEventRepository eventRepo;
    private final INotificationService notificationService;
    private final DiscountPolicyFactory discountPolicyFactory;
    private final PurchasePolicyFactory purchasePolicyFactory;

    public ProductionCompanyService(IProductionCompanyRepository repository, IAuth authService,
            IEventRepository eventRepo, INotificationService notificationService,
            DiscountPolicyFactory discountPolicyFactory, PurchasePolicyFactory purchasePolicyFactory) {
        this.repository = repository;
        this.authService = authService;
        this.eventRepo = eventRepo;
        this.notificationService = notificationService;
        this.discountPolicyFactory = discountPolicyFactory;
        this.purchasePolicyFactory = purchasePolicyFactory;
    }

    public ProductionCompanyDTO openProductionCompany(String companyName, String token) {
        logger.info("Attempting to open a new production company: '{}'", companyName);

        try {
            String founderId = getAuthenticatedUserId(token, "openProductionCompany");

            ProductionCompany existingCompany = repository.findById(companyName);
            if (existingCompany != null) {
                logger.error("Production company '{}' already exists", companyName);
                throw new IllegalArgumentException("Production company already exists");
            }

            logger.info("User '{}' attempting to open a new production company: '{}'", founderId, companyName);
            ProductionCompany company = new ProductionCompany(companyName, founderId);
            repository.store(company);
            logger.info("Successfully created production company: '{}'", companyName);

            return ProductionCompanyDTO.fromEntity(company);

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to open production company '{}': {}", companyName, e.getMessage());
            throw e;
        }
    }

    public Map<String, List<String>> getAllRoles(String companyID, String token) {
        String memberID = getAuthenticatedUserId(token, "getAllRoles");

        logger.info("Started getting all roles for production company: {}", companyID);
        ProductionCompany company = getCompanyOrThrow(companyID, "getAllRoles");

        if (!company.memberHasPermission(memberID, Permissions.VIEW_MANAGEMENT_HIERARCHY)) {
            logger.error("getAllRoles: User '{}' lacks VIEW_MANAGEMENT_HIERARCHY permission in company '{}'",
                    memberID, companyID);
            throw new IllegalArgumentException(
                    "Unauthorized: You do not have permission to view the management hierarchy.");
        }
        return company.getAllRoles(memberID);
    }

    public SalesReportDTO generateSalesReport(String token, String productionCompanyName) {

        try {
            String managerId = getAuthenticatedUserId(token, "generateSalesReport");

            ProductionCompany company = getCompanyOrThrow(productionCompanyName, "generateSalesReport");


            if (!company.memberHasPermission(managerId, Permissions.GENERATE_SALES_REPORT)) {
                logger.error("generateSalesReport: User '{}' lacks GENERATE_SALES_REPORT permission in company id={}",
                        managerId, productionCompanyName);
                throw new IllegalArgumentException("Unauthorized: You do not have permission to generate a sales report.");
            }

            logger.info("Generating sales report for manager='{}' in company id={}", managerId, productionCompanyName);

            List<String> subtree = company.getManagerSubtree(managerId);
            logger.info("Subtree for manager='{}' includes {} managers", managerId, subtree.size());

            int totalTickets = 0;
            double totalRevenue = 0.0;
            for (String mid : subtree) {
                for (Event event : eventRepo.findByManagerId(mid)) {
                    totalTickets += event.getTicketsSold();
                    totalRevenue += event.getTotalRevenue();
                }
            }

            logger.info("Sales report for manager='{}': tickets={}, revenue={}", managerId, totalTickets, totalRevenue);
            return new SalesReportDTO(managerId, productionCompanyName, totalTickets, totalRevenue, subtree);

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to generate sales report for manager='{}' in company id={}: {}", token, productionCompanyName,
                    e.getMessage());
            throw e;
        }
    }
    // ── UC16 - Change Purchase/Discount Policy ────────────────────────────────

    public ProductionCompanyDTO setCompanyPurchasePolicy(String token, String productionCompanyName, PurchasePolicyDTO dto) {
        try {
            String userId = getAuthenticatedUserId(token, "setCompanyPurchasePolicy");
            ProductionCompany company = getCompanyOrThrow(productionCompanyName, "setCompanyPurchasePolicy");

            if (!company.memberHasPermission(userId, Permissions.CHANGE_PURCHASE_POLICY)) {
                logger.error("setCompanyPurchasePolicy: User '{}' lacks permission in company id={}", userId, productionCompanyName);
                throw new IllegalArgumentException("Unauthorized: You do not have permission to manage policies.");
            }
            IPurchaseRule domainPurchaseRule = purchasePolicyFactory.createFromDTO(dto);

            company.setPurchasePolicy(domainPurchaseRule);

            repository.store(company);

            return ProductionCompanyDTO.fromEntity(company);

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to set purchase policy for company id={}: {}", productionCompanyName, e.getMessage());
            throw e;
        }
    }
    public ProductionCompanyDTO setCompanyDiscountPolicy(String token, String productionCompanyName, DiscountPolicyDTO dto) {
        try {
            String userId = getAuthenticatedUserId(token, "setCompanyDiscountPolicy");
            ProductionCompany company = getCompanyOrThrow(productionCompanyName, "setCompanyDiscountPolicy");

            if (!company.memberHasPermission(userId, Permissions.CHANGE_DISCOUNT_POLICY)) {
                logger.error("setCompanyDiscountPolicy: User '{}' lacks permission in company id={}", userId, productionCompanyName);
                throw new IllegalArgumentException("Unauthorized: You do not have permission to manage policies.");
            }
            IDiscountRule domainDiscountRule = discountPolicyFactory.createFromDTO(dto);

            company.setDiscountPolicy(domainDiscountRule);

            repository.store(company);

            return ProductionCompanyDTO.fromEntity(company);

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to set discount policy for company id={}: {}", productionCompanyName, e.getMessage());
            throw e;
        }
    }


    // ── UC24 - Suspend Production Company
    // ───────────────────────────────────
    public ProductionCompanyDTO suspendProductionCompany(String companyName, String token) {
        logger.info("Attempting to suspend/close production company: '{}'", companyName);
        try {
            String requestingUserId = getAuthenticatedUserId(token, "suspendProductionCompany");

            ProductionCompany company = getCompanyOrThrow(companyName, "suspendProductionCompany");
            // Get members to notify BEFORE suspension

            List<String> membersToNotify = company.getMembersToNotifyOnSuspension();
            // Domain handles authorization and state change

            company.suspend(requestingUserId);
            repository.store(company);
            // Deactivate all events for the production company

            List<Event> events = eventRepo.findByProductionCompanyId(companyName);
            for (Event event : events) {
                event.setActive(false);
                eventRepo.store(event);
            }

            // Notify members
            for (String memberEmail : membersToNotify) {
                notificationService.sendNotification(memberEmail, "Company Suspended", "The company " + companyName + " has been suspended.", companyName);
            }

            logger.info("Successfully suspended/closed production company '{}'", companyName);

            return ProductionCompanyDTO.fromEntity(company);

        } catch (IllegalArgumentException e) {
            logger.warn("Suspension failed for company '{}': {}", companyName, e.getMessage());
            throw e;
        }
    }

    public ProductionCompanyDTO reopenProductionCompany(String companyName, String token) {
        logger.info("Attempting to reopen production company: '{}'", companyName);

        try {
            String requestingUserId = getAuthenticatedUserId(token, "reopenProductionCompany");

            ProductionCompany company = getCompanyOrThrow(companyName, "reopenProductionCompany");

            // Get members to notify before reopen
            List<String> membersToNotify = company.getMembersToNotifyOnReopen();

            company.reopen(requestingUserId);
            repository.store(company);

            // Notify members
            notifyMembersOnCompanyReopen(membersToNotify, companyName);

            logger.info("Successfully reopened production company '{}'", companyName);

            return ProductionCompanyDTO.fromEntity(company);

        } catch (IllegalArgumentException e) {
            logger.warn("Reopen failed for company '{}': {}", companyName, e.getMessage());
            throw e;
        }
    }

    private void notifyMembersOnCompanyReopen(List<String> membersToNotify, String companyName) {
        for (String memberEmail : membersToNotify) {
            notificationService.sendNotification(memberEmail, "Company Reopened", "The company " + companyName + " has been reopened.", companyName);
        }
    }


    private String getAuthenticatedUserId(String token, String actionName) {
        if (!authService.validateToken(token)) {
            logger.error("{}: Invalid token", actionName);
            throw new UnauthorizedException("Invalid token");
        }
        String userId = authService.extractEmailFromToken(token);
        if (userId == null) {
            logger.error("{}: Could not extract email from token", actionName);
            throw new IllegalArgumentException("Could not extract email from token");
        }
        return userId;
    }

    private ProductionCompany getCompanyOrThrow(String companyId, String actionName) {
        ProductionCompany company = repository.findById(companyId);
        if (company == null) {
            logger.error("{}: Production company id={} not found", actionName, companyId);
            throw new IllegalArgumentException("Production company not found");
        }
        return company;
    }


    public ProductionCompanyDTO getCompanyInfo(String companyId) {
        ProductionCompany company = repository.findById(companyId);
        if (company == null)
            throw new CompanyNotFoundException(companyId);
        return ProductionCompanyDTO.fromEntity(company);
    }

    public List<RoleDTO> getCompanyRoles(String companyId) {
        ProductionCompany company = repository.findById(companyId);
        if (company == null) throw new CompanyNotFoundException(companyId);
        return company.getRoles().values().stream().map(RoleDTO::fromEntity).toList();
    }

    public List<ProductionCompanyDTO> getAllCompanies() {
        return repository.getAll().stream()
                .map(ProductionCompanyDTO::fromEntity)
                .toList();
    }

    public String getEventName(String eventId) {
        Event event = eventRepo.findById(eventId);
        return event != null ? event.getName() : eventId;
    }

    public DiscountPolicyDTO getDiscountPolicy(String companyId) {
        ProductionCompany company = getCompanyOrThrow(companyId, "discountPolicy");
        return DiscountPolicyDTO.fromEntity(company.getDiscountPolicy());
    }

    public PurchasePolicyDTO getPurchasePolicy(String companyId) {
        ProductionCompany company = getCompanyOrThrow(companyId, "purchasePolicy");
        return PurchasePolicyDTO.fromEntity(company.getPurchasePolicy());
    }

}
