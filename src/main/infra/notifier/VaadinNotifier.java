package infra.notifier;

import application.interfaces.INotifier;
import domain.Aggregates.notification.Notification;
import domain.Aggregates.notification.AppointmentNotification;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.server.VaadinSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;

@Component
public class VaadinNotifier implements INotifier {

    private static final Logger logger = LoggerFactory.getLogger(VaadinNotifier.class);
    private static final String REGISTRATION_KEY = "notification.registration";
    private static final String APPOINTMENT_HANDLER_KEY = "notification.appointmentHandler";

    @Override
    public boolean notifyUser(String memberId, Notification notification) {
        return Broadcaster.broadcast(memberId, notification);
    }

    @Override
    public void register(String memberId) {
        UI ui = UI.getCurrent();
        if (ui == null) return;

        Registration reg = Broadcaster.register(memberId, notification -> {
            ui.access(() -> renderNotification(notification));
        });

        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(REGISTRATION_KEY, reg);
        }
    }

    @Override
    public void setAppointmentHandler(BiConsumer<AppointmentNotification, Boolean> handler) {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(APPOINTMENT_HANDLER_KEY, handler);
        }
    }

    private void renderNotification(Notification notification) {
        if (notification instanceof AppointmentNotification apptNote) {
            Dialog dialog = new Dialog();
            dialog.setCloseOnEsc(false);
            dialog.setCloseOnOutsideClick(false);
            dialog.setWidth("640px");

            H3 title = new H3("Appointment");
            Paragraph content = new Paragraph(apptNote.getContent());

            Button acceptBtn = new Button("Accept", e -> {
                handleAppointmentAction(apptNote, true);
                dialog.close();
            });
            acceptBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);

            Button rejectBtn = new Button("Reject", e -> {
                handleAppointmentAction(apptNote, false);
                dialog.close();
            });
            rejectBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);

            Button closeBtn = new Button("Close", e -> dialog.close());
            closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

            HorizontalLayout buttons = new HorizontalLayout(acceptBtn, rejectBtn, closeBtn);
            buttons.setAlignItems(FlexComponent.Alignment.CENTER);

            dialog.add(title, content, buttons);
            dialog.open();
        } else {
            Dialog dialog = new Dialog();
            dialog.setCloseOnEsc(false);
            dialog.setCloseOnOutsideClick(false);
            dialog.setWidth("520px");

            H3 title = new H3("Notification");
            Paragraph content = new Paragraph(notification.getContent());
            Button closeBtn = new Button("Close", e -> dialog.close());
            closeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

            HorizontalLayout footer = new HorizontalLayout(closeBtn);
            footer.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

            dialog.add(title, content, footer);
            dialog.open();
        }
    }

    @SuppressWarnings("unchecked")
    private void handleAppointmentAction(AppointmentNotification notification, boolean accepted) {
        try {
            VaadinSession session = VaadinSession.getCurrent();
            if (session == null) return;
            BiConsumer<AppointmentNotification, Boolean> handler =
                    (BiConsumer<AppointmentNotification, Boolean>) session.getAttribute(APPOINTMENT_HANDLER_KEY);
            if (handler == null) return;
            handler.accept(notification, accepted);
        } catch (Exception ex) {
            logger.error("Failed to process appointment action: {}", ex.getMessage());
            com.vaadin.flow.component.notification.Notification.show(
                    "Error processing appointment: " + ex.getMessage(),
                    5000, com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    @Override
    public void unregister(String memberId) {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            Registration reg = (Registration) session.getAttribute(REGISTRATION_KEY);
            if (reg != null) {
                reg.remove();
                session.setAttribute(REGISTRATION_KEY, null);
            }
            session.setAttribute(APPOINTMENT_HANDLER_KEY, null);
        }
    }
}
