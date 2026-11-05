package presentation.view;

import domain.Aggregates.notification.AppointmentNotification;
import domain.Aggregates.notification.Notification;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import presentation.CurrentUser;
import presentation.Result;
import presentation.presenter.NotificationsPresenter;

import java.util.List;

@Route(value = "member/notifications", layout = MainLayout.class)
@PageTitle("Notifications | Ticketanic")
@AnonymousAllowed
public class NotificationsView extends VerticalLayout implements BeforeEnterObserver {

    private final NotificationsPresenter presenter;
    private final CurrentUser currentUser;
    private final VerticalLayout content = new VerticalLayout();

    public NotificationsView(NotificationsPresenter presenter, CurrentUser currentUser) {
        this.presenter = presenter;
        this.currentUser = currentUser;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("background", "var(--lumo-contrast-5pct)");

        H2 title = new H2("Notifications");
        title.getStyle().set("margin-top", "0");

        content.setPadding(false);
        content.setSpacing(true);
        content.setWidthFull();

        add(title, content);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!currentUser.isLoggedIn()) {
            event.forwardTo("login");
            return;
        }
        loadNotifications();
    }

    private void loadNotifications() {
        content.removeAll();

        Result<List<Notification>> result = presenter.getPendingNotifications();
        if (result instanceof Result.Fail<List<Notification>> fail) {
            com.vaadin.flow.component.notification.Notification n =
                    com.vaadin.flow.component.notification.Notification.show(fail.message());
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        List<Notification> notifications = ((Result.Ok<List<Notification>>) result).value();
        if (notifications.isEmpty()) {
            content.add(buildEmptyState());
            return;
        }

        for (Notification notification : notifications) {
            content.add(buildCard(notification));
        }
    }

    private Div buildCard(Notification notification) {
        if (notification instanceof AppointmentNotification appt) {
            return buildAppointmentCard(appt);
        }
        return buildRegularCard(notification);
    }

    private Div buildRegularCard(Notification notification) {
        Div card = newCard();

        H3 subject = new H3(notification.getSubject());
        subject.getStyle().set("margin-top", "0").set("margin-bottom", "var(--lumo-space-s)");

        Paragraph body = new Paragraph(notification.getContent());
        body.getStyle().set("color", "var(--lumo-secondary-text-color)").set("margin", "0");

        Button dismissBtn = new Button("Dismiss", e -> {
            Result<Void> res = presenter.dismiss(notification.getId());
            if (res instanceof Result.Fail<Void> fail) {
                showError(fail.message());
            } else {
                loadNotifications();
            }
        });
        dismissBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout footer = new HorizontalLayout(dismissBtn);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        footer.getStyle().set("margin-top", "var(--lumo-space-m)");

        card.add(subject, body, footer);
        return card;
    }

    private Div buildAppointmentCard(AppointmentNotification notification) {
        Div card = newCard();
        card.getStyle().set("border-left", "4px solid var(--lumo-primary-color)");

        Span badge = new Span("Appointment");
        badge.getStyle()
                .set("background", "var(--lumo-primary-color-10pct)")
                .set("color", "var(--lumo-primary-text-color)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("padding", "2px 8px")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("font-weight", "600");

        H3 subject = new H3(notification.getSubject());
        subject.getStyle().set("margin-top", "var(--lumo-space-xs)").set("margin-bottom", "var(--lumo-space-s)");

        Paragraph body = new Paragraph(notification.getContent());
        body.getStyle().set("color", "var(--lumo-secondary-text-color)").set("margin", "0");

        Button acceptBtn = new Button("Accept", e -> {
            Result<Void> res = presenter.respondToAppointment(notification, true);
            if (res instanceof Result.Fail<Void> fail) {
                showError(fail.message());
            } else {
                com.vaadin.flow.component.notification.Notification.show("Appointment accepted.");
                getUI().ifPresent(ui -> ui.navigate(NotificationsView.class));
            }
        });
        acceptBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);

        Button declineBtn = new Button("Decline", e -> {
            Result<Void> res = presenter.respondToAppointment(notification, false);
            if (res instanceof Result.Fail<Void> fail) {
                showError(fail.message());
            } else {
                com.vaadin.flow.component.notification.Notification.show("Appointment declined.");
                getUI().ifPresent(ui -> ui.navigate(NotificationsView.class));
            }
        });
        declineBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);

        HorizontalLayout footer = new HorizontalLayout(acceptBtn, declineBtn);
        footer.setSpacing(true);
        footer.getStyle().set("margin-top", "var(--lumo-space-m)");

        card.add(badge, subject, body, footer);
        return card;
    }

    private Div newCard() {
        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("box-shadow", "var(--lumo-box-shadow-s)")
                .set("padding", "var(--lumo-space-l)");
        return card;
    }

    private Div buildEmptyState() {
        Div card = newCard();
        card.getStyle()
                .set("text-align", "center")
                .set("padding", "var(--lumo-space-xl)");

        H3 heading = new H3("No notifications");
        heading.getStyle().set("margin-top", "0");

        Paragraph p = new Paragraph("You're all caught up.");
        p.getStyle().set("color", "var(--lumo-secondary-text-color)");

        card.add(heading, p);
        return card;
    }

    private void showError(String message) {
        com.vaadin.flow.component.notification.Notification n =
                com.vaadin.flow.component.notification.Notification.show(message, 4000,
                        com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
