//package domain.Aggregates.notification;
//
//import domain.Aggregates.productioncompany.Permissions;
//
//import java.util.Set;
//import java.util.UUID;
//
//public class NotificationFactory {
//    public static Notification createManagerAppointment(String companyID, String appointerID, Set<Permissions> permissions) {
//        String id = UUID.randomUUID().toString();
//        return new AppointmentNotification(companyID, appointerID, permissions, id);
//    }
//
//    public static Notification createOwnerAppointment(String companyID, String appointerID) {
//        String id = UUID.randomUUID().toString();
//        return new OwnerAppointmentNotification(companyID, appointerID, id);
//    }
//
//    public static Notification createCompanyClosure(String companyID) {
//        String id = UUID.randomUUID().toString();
//        return new CompanyClosureNotification(companyID, id);
//    }
//
//    public static Notification createCompanySuspension(String companyID) {
//        String id = UUID.randomUUID().toString();
//        return new CompanySuspensionNotification(companyID, id);
//    }
//
//    public static Notification createCompanyReopen(String companyID) {
//        String id = UUID.randomUUID().toString();
//        return new CompanyReopenNotification(companyID, id);
//    }
//
//    public static Notification createPurchaseSuccess(String orderId) {
//        String id = UUID.randomUUID().toString();
//        return new PurchaseSuccessNotification(orderId, id);
//    }
//}
