package presentation.view;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import presentation.CurrentUser;
import presentation.Result;
import presentation.presenter.AdminPresenter;

import java.util.function.Function;

@Route(value = "admin/members", layout = MainLayout.class)
@PageTitle("Member Management")
public class MemberManagementView extends VerticalLayout implements BeforeEnterObserver {

    private final AdminPresenter presenter;
    private final CurrentUser currentUser;

    public MemberManagementView(AdminPresenter presenter, CurrentUser currentUser) {
        this.presenter = presenter;
        this.currentUser = currentUser;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!currentUser.isLoggedIn() || !currentUser.isAdmin()) {
            event.forwardTo("access-denied");
            return;
        }
        loadPage();
    }

    private void loadPage() {
        removeAll();
        add(buildHeader());

        FlexLayout cards = new FlexLayout();
        cards.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        cards.getStyle().set("gap", "24px").set("padding", "2rem");

        cards.add(buildActionCard(
                "Suspend Member",
                "Block a member from accessing the system.",
                () -> openEmailActionDialog(
                        "Suspend Member",
                        "Member Email",
                        "Suspend",
                        ButtonVariant.LUMO_ERROR,
                        presenter::suspendMember)));

        cards.add(buildActionCard(
                "Unsuspend Member",
                "Restore access for a currently suspended member.",
                () -> openEmailActionDialog(
                        "Unsuspend Member",
                        "Member Email",
                        "Unsuspend",
                        ButtonVariant.LUMO_PRIMARY,
                        presenter::unsuspendMember)));

        cards.add(buildActionCard(
                "Cancel Membership",
                "Permanently cancel a member's subscription.",
                () -> openEmailActionDialog(
                        "Cancel Membership",
                        "Member Email",
                        "Cancel Membership",
                        ButtonVariant.LUMO_ERROR,
                        presenter::cancelMembership)));

        cards.add(buildActionCard(
                "Suspension History",
                "View currently suspended members or full suspension history.",
                () -> UI.getCurrent().navigate("admin/members/history")));

        add(cards);

        Button back = new Button("Back", e -> UI.getCurrent().navigate("admin"));
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        back.getStyle().set("margin", "0 2rem 2rem");
        add(back);
    }

    private HorizontalLayout buildHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.getStyle()
                .set("background", "linear-gradient(135deg, var(--lumo-primary-color), #141d2e)")
                .set("padding", "2rem")
                .set("width", "100%")
                .set("align-items", "center");
        H2 title = new H2("Member Management");
        title.getStyle().set("margin", "0").set("color", "white").set("flex", "1");
        header.add(title);
        return header;
    }

    private VerticalLayout buildActionCard(String title, String description, Runnable onClick) {
        VerticalLayout card = new VerticalLayout();
        card.getStyle()
                .set("border", "1px solid #e0e0e0")
                .set("border-radius", "12px")
                .set("padding", "32px")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.1)")
                .set("background", "white")
                .set("cursor", "pointer")
                .set("width", "300px");

        H3 cardTitle = new H3(title);
        cardTitle.getStyle().set("margin", "0 0 0.5rem 0");

        Paragraph desc = new Paragraph(description);
        desc.getStyle().set("color", "var(--lumo-secondary-text-color)").set("margin", "0");

        card.add(cardTitle, desc);
        card.addClickListener(e -> onClick.run());
        return card;
    }

    /**
     * Generic dialog: one EmailField, one action button, one cancel button.
     * The action is a presenter method that receives the email and returns Result<Void>.
     */
    private void openEmailActionDialog(String dialogTitle,
                                       String fieldLabel,
                                       String actionLabel,
                                       ButtonVariant actionVariant,
                                       Function<String, Result<Void>> action) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(dialogTitle);
        dialog.setWidth("400px");

        EmailField emailField = new EmailField(fieldLabel);
        emailField.setWidthFull();
        emailField.setRequiredIndicatorVisible(true);

        Paragraph errorMsg = new Paragraph();
        errorMsg.getStyle().set("color", "var(--lumo-error-color)").set("margin", "0");
        errorMsg.setVisible(false);

        VerticalLayout content = new VerticalLayout(emailField, errorMsg);
        content.setPadding(false);
        content.setSpacing(true);
        dialog.add(content);

        Button cancelBtn = new Button("Cancel", e -> dialog.close());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button actionBtn = new Button(actionLabel, e -> {
            String email = emailField.getValue().trim();
            if (email.isEmpty()) {
                errorMsg.setText("Email is required.");
                errorMsg.setVisible(true);
                return;
            }
            Result<Void> result = action.apply(email);
            if (result instanceof Result.Ok) {
                dialog.close();
                Notification success = Notification.show(actionLabel + " successful for: " + email);
                success.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
                errorMsg.setText(((Result.Fail<Void>) result).message());
                errorMsg.setVisible(true);
            }
        });
        actionBtn.addThemeVariants(actionVariant);

        dialog.getFooter().add(cancelBtn, actionBtn);
        dialog.open();
    }
}