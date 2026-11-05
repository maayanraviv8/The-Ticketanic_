package application.service;

import domain.Aggregates.member.Suspension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import application.dto.OrderDTO;
import application.dto.SuspensionDTO;
import application.interfaces.IAuth;
import application.interfaces.INotificationService;
import domain.Aggregates.event.Event;
import domain.Aggregates.member.Member;
import domain.Aggregates.order.Order;
import domain.Aggregates.productioncompany.ProductionCompany;
import domain.Irepository.IAdminRepository;
import domain.Irepository.IEventRepository;
import domain.Irepository.IMemberRepository;
import domain.Irepository.IOrderRepository;
import domain.Irepository.IProductionCompanyRepository;
import domain.exceptions.AdminException;
import domain.exceptions.MemberException;
import domain.exceptions.ProductionCompanyException;
import domain.exceptions.UnauthorizedException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

    private final IOrderRepository orderRepository;
    private final IAdminRepository adminRepository;
    private final IMemberRepository memberRepository;
    private final IEventRepository eventRepository;
    private final IProductionCompanyRepository productionCompanyRepository;
    private final IAuth authService;
    private final INotificationService notificationService;

    public AdminService(IOrderRepository orderRepository,
                        IAdminRepository adminRepository,
                        IMemberRepository memberRepository,
                        IEventRepository eventRepository,
                        IProductionCompanyRepository productionCompanyRepository,
                        IAuth authService,
                        INotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.adminRepository = adminRepository;
        this.memberRepository = memberRepository;
        this.eventRepository = eventRepository;
        this.productionCompanyRepository = productionCompanyRepository;
        this.authService = authService;
        this.notificationService = notificationService;
    }

    private String authenticate(String token) {
        if (token == null || !authService.validateToken(token))
            throw new UnauthorizedException("Token is invalid or has expired");
        String userId = authService.extractEmailFromToken(token);
        if (userId == null) throw new UnauthorizedException("Could not identify user from token");
        return userId;
    }

    public Map<String, List<OrderDTO>> getGlobalHistoryByBuyers(String token, LocalDateTime startDate, LocalDateTime endDate) {
        String adminEmail = authenticateAdmin(token);
        logger.info("Admin '{}' fetching global purchase history by buyers", adminEmail);

        Map<String, List<OrderDTO>> result = new HashMap<>();
        for (Member member : memberRepository.getAll()) {
            List<OrderDTO> dtos = new ArrayList<>();
            for (String orderId : member.getOrderIds()) {
                Order order = orderRepository.findById(orderId);
                if (order != null && isWithinDateRange(order.getPurchaseTime(), startDate, endDate)) {
                    dtos.add(convertToDTO(order));
                }
            }
            if (!dtos.isEmpty()) {
                result.put(member.getEmail(), dtos);
            }
        }
        return result;
    }

    public Map<String, Map<String, List<OrderDTO>>> getGlobalHistoryByCompaniesAndEvents(String token, LocalDateTime startDate, LocalDateTime endDate) {
        String adminEmail = authenticateAdmin(token);
        logger.info("Admin '{}' fetching global purchase history by companies and events", adminEmail);

        Map<String, Map<String, List<OrderDTO>>> result = new HashMap<>();
        for (Order order : orderRepository.getAll()) {
            if (!isWithinDateRange(order.getPurchaseTime(), startDate, endDate)) continue;

            String eventId = order.getEventId();
            Event event = eventRepository.findById(eventId);
            if (event == null) continue;

            String companyId = event.getProductionCompanyName();

            result.computeIfAbsent(companyId, k -> new HashMap<>())
                  .computeIfAbsent(eventId, k -> new ArrayList<>())
                  .add(convertToDTO(order));
        }
        return result;
    }

    public void closeProductionCompany(String token, String companyId) {
        String adminEmail = authenticateAdmin(token);
        logger.info("Admin '{}' attempting to close production company '{}'", adminEmail, companyId);

        ProductionCompany company = productionCompanyRepository.findById(companyId);
        if (company == null) {
            logger.warn("Close company failed: company '{}' not found", companyId);
            throw new ProductionCompanyException("Production company not found: " + companyId);
        }
        if (!company.isActive()) {
            logger.warn("Close company failed: company '{}' is already closed", companyId);
            throw new ProductionCompanyException("Production company is already closed: " + companyId);
        }

        // Revoke all Owners and Managers (Founder is preserved)
        List<String> revokedMembers = company.revokeAllOwnersAndManagers();

        // Set company inactive
        company.setInactive();
        productionCompanyRepository.store(company);

        // Send notifications: Founder + all revoked Owners and Managers
        String founderId = company.getFounderID();
        sendClosureNotification(founderId, companyId);
        for (String memberEmail : revokedMembers) {
            sendClosureNotification(memberEmail, companyId);
        }

        logger.info("Successfully closed production company '{}'. Notified: {} members + founder '{}'",
                    companyId, revokedMembers.size(), founderId);
    }

    public void cancelMembership(String token, String memberId) {
        String adminEmail = authenticateAdmin(token);
        logger.info("Admin '{}' attempting to cancel membership of '{}'", adminEmail, memberId);

        Member member = memberRepository.findById(memberId);
        if (member == null) {
            logger.warn("Cancel membership failed: member '{}' not found", memberId);
            throw new MemberException("Member not found: " + memberId);
        }

        // Block cancellation if member is the founder of an active company
        for (ProductionCompany company : productionCompanyRepository.getAll()) {
            if (company.isActive() && company.memberIsFounder(memberId)) {
                logger.warn("Cancel membership failed: member '{}' is founder of active company '{}'",
                            memberId, company.getName());
                throw new MemberException("Cannot cancel member: they are the founder of active company '"
                        + company.getName() + "'.");
            }
        }
        // Revoke roles in all production companies (including FounderRole)
        for (ProductionCompany company : productionCompanyRepository.getAll()) {
            if (company.removeMember(memberId)) {
                productionCompanyRepository.store(company);
            }
        }

        // Mark member as inactive
        member.cancelMembership();
        memberRepository.store(member);

        logger.info("Successfully canceled membership of '{}'", memberId);
    }

    public void suspendMember(String token, String memberEmail) {
        String adminEmail = authenticateAdmin(token);
        logger.info("Admin '{}' attempting to suspend member '{}'", adminEmail, memberEmail);

        Member member = memberRepository.findById(memberEmail);
        if (member == null) {
            logger.warn("Suspend failed: member '{}' not found", memberEmail);
            throw new MemberException("Member not found: " + memberEmail);
        }
        if (!member.isActive()) {
            logger.warn("Suspend failed: member '{}' account is cancelled", memberEmail);
            throw new MemberException("Cannot suspend a cancelled member: " + memberEmail);
        }
        if (adminRepository.findById(memberEmail) != null) {
            logger.warn("Suspend failed: target '{}' is a System Admin", memberEmail);
            throw new MemberException("Cannot suspend a System Admin: " + memberEmail);
        }

        member.suspend();
        memberRepository.store(member);
        logger.info("Successfully suspended member '{}'", memberEmail);    }

    public void unsuspendMember(String token, String memberEmail) {
        String adminEmail = authenticateAdmin(token);
        logger.info("Admin '{}' attempting to unsuspend member '{}'", adminEmail, memberEmail);

        Member member = memberRepository.findById(memberEmail);
        if (member == null) {
            logger.warn("Unsuspend failed: member '{}' not found", memberEmail);
            throw new MemberException("Member not found: " + memberEmail);
        }
        member.unsuspend();
        memberRepository.store(member);
        logger.info("Successfully unsuspended member '{}'", memberEmail);    }

    public List<SuspensionDTO> getMemberSuspensions(String token, boolean activeOnly) {
        String adminEmail = authenticateAdmin(token);
        logger.info("Admin '{}' fetching member suspensions (activeOnly={})", adminEmail, activeOnly);

        List<SuspensionDTO> result = new ArrayList<>();
        for (Member member : memberRepository.getAll()) {
            for (Suspension suspension : member.getFilteredSuspensions(activeOnly)) {
                result.add(SuspensionDTO.from(member.getEmail(), suspension));
            }
        }
        return result;
    }

    private void verifyAdmin(String adminId) {
        if (adminRepository.findById(adminId) == null) {
            throw new AdminException("Action denied: User is not a System Admin");
        }
    }

    private String authenticateAdmin(String token) {
        String adminEmail = authenticate(token);
        verifyAdmin(adminEmail);
        return adminEmail;
    }

    private void sendClosureNotification(String memberEmail, String companyId) {
        Member member = memberRepository.findById(memberEmail);
        if (member == null) {
            logger.warn("Cannot notify member '{}' about closure of company '{}': member not found",
                        memberEmail, companyId);
            return;
        }
        notificationService.sendNotification(memberEmail, "Company Closed", "The company " + companyId + " has been closed.", companyId);
        // Associations handled via recipientEmail in repository
    }

    private OrderDTO convertToDTO(Order order) {
        return new OrderDTO(
                order.getOrderId(),
                order.getEventId(),
                order.getShowId(),
                new ArrayList<>(order.getTicketIds()),
                order.getPrice(),
                order.getPurchaseTime()
        );
    }

    private boolean isWithinDateRange(LocalDateTime date, LocalDateTime start, LocalDateTime end) {
        if (date == null) return true;
        if (start != null && date.isBefore(start)) return false;
        if (end != null && date.isAfter(end)) return false;
        return true;
    }

    public boolean isAdmin(String email) {
        return adminRepository.findById(email) != null;
    }
}