package application.interfaces;

import domain.Aggregates.notification.AppointmentNotification;
import domain.Aggregates.notification.Notification;

import java.util.function.BiConsumer;

public interface INotifier {
    /** Returns true if delivered immediately, false if recipient has no active listener. */
    boolean notifyUser(String memberId, Notification notification);

    void register(String memberId);

    void unregister(String memberId);

    void setAppointmentHandler(BiConsumer<AppointmentNotification, Boolean> handler);
}
