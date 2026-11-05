package application.service;

import application.dto.RoleDTO;
import application.interfaces.IAuth;
import application.interfaces.INotificationService;
import application.interfaces.IRoleService;
import domain.Aggregates.notification.AppointmentNotification;
import domain.Aggregates.notification.Notification;
import domain.Aggregates.productioncompany.Permissions;
import domain.Aggregates.productioncompany.ProductionCompany;
import domain.Aggregates.productioncompany.RoleState;
import domain.Irepository.IProductionCompanyRepository;
import domain.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Service
@Transactional
public class RoleService implements IRoleService {
    private static final Logger logger = LoggerFactory.getLogger(RoleService.class);
    private final IProductionCompanyRepository companyRepo;
    private final IAuth authService;
    private final INotificationService notificationService;

    public RoleService(IProductionCompanyRepository companyRepo, IAuth authService, INotificationService notificationService) {
        this.companyRepo = companyRepo;
        this.authService = authService;
        this.notificationService = notificationService;
    }

    public void appointManager(String token, String companyID, String newManagerID, Set<Permissions> permissions) throws Exception{
        String appointerID = validateAndExtractID(token);
        ProductionCompany company = getCompanyAndCheckIfActive(companyID);

        if (!company.memberHasPermission(appointerID, Permissions.APPOINT_MANAGERS)) {
            logger.warn("Member '{}' does not have permission to appoint managers in '{}' company", appointerID, companyID);
            throw new AppointmentPermissionException("manager");
        }
        memberAlreadyHasRole(newManagerID, company);
        companyRepo.store(company);

        notificationService.sendAppointment(newManagerID, companyID, appointerID, "Appointment Request", permissions);
        logger.info("Appointment notification sent to member '{}' for manager role in company '{}'", newManagerID, companyID);
    }

    public void appointOwner(String token, String companyID, String newOwnerID) throws Exception{
        String appointerID = validateAndExtractID(token);
        ProductionCompany company = getCompanyAndCheckIfActive(companyID);

        if (!company.memberHasPermission(appointerID, Permissions.APPOINT_OWNERS)) {
            logger.warn("Member '{}' does not have permission to appoint owners in '{}' company", appointerID, companyID);
            throw new AppointmentPermissionException("owner");
        }
        memberAlreadyHasRole(newOwnerID, company);
        companyRepo.store(company);

        notificationService.sendAppointment(newOwnerID, companyID, appointerID, "Owner Appointment Request", null);
        logger.info("Appointment notification sent to member '{}' for owner role in company '{}'", newOwnerID, companyID);
    }

    public void acceptAppointment(String token, String companyID, boolean accepted) throws Exception{
        String memberID = validateAndExtractID(token);
        
        // Find appointment notifications for this member
        List<Notification> pending = notificationService.getPendingNotifications(memberID);
        AppointmentNotification notification = (AppointmentNotification) pending.stream()
                .filter(n -> n instanceof AppointmentNotification && companyID.equals(((AppointmentNotification) n).getCompanyID()))
                .findFirst()
                .orElse(null);

        if (notification == null) {
            logger.warn("No appointment notification found for member '{}' in company '{}'", memberID, companyID);
            return;
        }

        ProductionCompany company = getCompanyAndCheckIfActive(companyID);
        if ("Appointment Request".equals(notification.getSubject())) {
            company.acceptAppointment(notification.getAppointerID(), memberID, notification.getPermissions(), accepted);
        } else if ("Owner Appointment Request".equals(notification.getSubject())) {
            company.acceptAppointment(notification.getAppointerID(), memberID, accepted);
        }

        companyRepo.store(company);
        logger.info("Member '{}' returned '{}' for the appointment in company '{}'", memberID, accepted, companyID);

        // Remove the notification from the system
        notificationService.removeNotification(notification.getId());
    }

    public void removeAppointment(String token, String companyID, String removeID) throws Exception{
        String appointerID = validateAndExtractID(token);
        ProductionCompany company = getCompany(companyID);

        company.removeAppointment(appointerID, removeID);
        companyRepo.store(company);
        logger.info("Successfully removed appointment of '{}' from company '{}' by '{}'", removeID, companyID, appointerID);
    }

    public void relinquishOwnership(String token, String companyID) throws Exception {
        String ownerID = validateAndExtractID(token);
        ProductionCompany company = getCompany(companyID);

        company.relinquishOwnership(ownerID);
        companyRepo.store(company);
        logger.info("Successfully relinquished ownership of '{}' in company '{}'", ownerID, companyID);
    }

    public void addManagerPermission(String token, String companyID, String memberID, Set<Permissions> permissions) throws Exception{
        String appointedID = validateAndExtractID(token);
        ProductionCompany company = getCompanyAndCheckIfActive(companyID);

        company.addManagerPermission(memberID, appointedID, permissions);
        companyRepo.store(company);
        logger.info("Successfully added permissions {} to manager '{}' in company '{}'", permissions, memberID, companyID);
    }

    public void removeManagerPermission(String token, String companyID, String memberID, Set<Permissions> permissions) throws Exception{
        String appointedID = validateAndExtractID(token);
        ProductionCompany company = getCompanyAndCheckIfActive(companyID);

        company.removeManagerPermissions(memberID, appointedID, permissions);
        companyRepo.store(company);
        logger.info("Successfully removed permissions {} from manager '{}' in company '{}'", permissions, memberID, companyID);
    }

    private String validateAndExtractID(String token) throws Exception {
        if (!authService.validateToken(token))
            throw new UnauthorizedException("Invalid token provided");
        String id = authService.extractEmailFromToken(token);
        if (id == null)
            throw new Exception("Did not extract email from token");
        return id;
    }

    private ProductionCompany getCompany(String companyID) {
        ProductionCompany company = companyRepo.findById(companyID);
        if (company == null)
            throw new CompanyNotFoundException(companyID);
        return company;
    }

    private ProductionCompany getCompanyAndCheckIfActive(String companyID) {
        ProductionCompany company = getCompany(companyID);
        if (!company.isActive()) {
            logger.warn("Production company '{}' is not active", companyID);
            throw new IllegalArgumentException("Production company is not active");
        }
        return company;
    }

    private void memberAlreadyHasRole(String memberID, ProductionCompany company) throws Exception {
        if (company.memberHasPermission(memberID)) {
            logger.warn("Member '{}' already has a role in company '{}'", memberID, company.getName());
            throw new MemberHasRoleException(memberID);
        }
        if(company.isPendingAppointee(memberID)) {
            logger.warn("Member '{}' already has a pending appointment in company '{}'", memberID, company.getName());
            throw new IllegalArgumentException("Member already has a pending appointment");
        }
        else{
            company.addPendingAppointee(memberID);
        }
    }

    public Map<String, RoleDTO> getUserRoles(String memberID) {
        Map<String, RoleDTO> result = new HashMap<>();
        for (ProductionCompany company : companyRepo.getAll()) {
            RoleState role = company.getMemberRole(memberID);
            if (role != null)
                result.put(company.getName(), RoleDTO.fromEntity(role));
        }
        return result;
    }

}
