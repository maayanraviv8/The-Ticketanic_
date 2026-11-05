package presentation.view;

import application.dto.MemberDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import presentation.CurrentUser;
import presentation.Result;
import presentation.presenter.ProfilePresenter;

@Route(value = "member/profile", layout = MainLayout.class)
@PageTitle("My Profile | Ticketanic")
@AnonymousAllowed
public class ProfileView extends VerticalLayout implements BeforeEnterObserver {

    private final ProfilePresenter presenter;
    private final CurrentUser currentUser;

    private final Span usernameValue = new Span();
    private final Span emailValue = new Span();
    private final TextField usernameField = new TextField("New username");
    private final PasswordField newPasswordField = new PasswordField("New password");
    private final PasswordField confirmPasswordField = new PasswordField("Confirm new password");

    public ProfileView(ProfilePresenter presenter, CurrentUser currentUser) {
        this.presenter = presenter;
        this.currentUser = currentUser;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("background", "var(--lumo-contrast-5pct)");

        H2 title = new H2("My Profile");
        title.getStyle().set("margin-top", "0");

        add(title, buildDetailsCard(), buildUsernameCard(), buildPasswordCard());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!currentUser.isLoggedIn()) {
            event.forwardTo("login");
            return;
        }
        loadDetails();
    }

    private void loadDetails() {
        Result<MemberDTO> result = presenter.loadProfile();
        if (result instanceof Result.Ok<MemberDTO> ok) {
            applyDetails(ok.value());
        } else if (result instanceof Result.Fail<MemberDTO> fail) {
            showError(fail.message());
            applyDetails(MemberDTO.builder()
                    .username(currentUser.getUsername())
                    .email(currentUser.getEmail())
                    .build());
        }
    }

    private void applyDetails(MemberDTO dto) {
        usernameValue.setText(dto.getUsername() != null ? dto.getUsername() : "—");
        emailValue.setText(dto.getEmail() != null ? dto.getEmail() : "—");
        usernameField.setValue(dto.getUsername() != null ? dto.getUsername() : "");
    }

    private Div buildDetailsCard() {
        H3 heading = new H3("Account details");
        heading.getStyle().set("margin-top", "0").set("margin-bottom", "var(--lumo-space-m)");

        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "auto 1fr")
                .set("gap", "var(--lumo-space-s) var(--lumo-space-l)");
        grid.add(styledLabel("Username"), usernameValue);
        grid.add(styledLabel("Email"), emailValue);

        Span hint = new Span("Email cannot be changed.");
        hint.getStyle()
                .set("display", "block")
                .set("margin-top", "var(--lumo-space-m)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");

        return card(heading, grid, hint);
    }

    private Div buildUsernameCard() {
        H3 heading = new H3("Change username");
        heading.getStyle().set("margin-top", "0").set("margin-bottom", "var(--lumo-space-m)");

        usernameField.setWidthFull();
        usernameField.setRequiredIndicatorVisible(true);

        Button save = new Button("Save username");
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.getStyle().set("margin-top", "var(--lumo-space-s)");
        save.addClickListener(e -> submitUsername(save));

        return card(heading, usernameField, save);
    }

    private Div buildPasswordCard() {
        H3 heading = new H3("Change password");
        heading.getStyle().set("margin-top", "0").set("margin-bottom", "var(--lumo-space-m)");

        newPasswordField.setWidthFull();
        newPasswordField.setRequiredIndicatorVisible(true);

        confirmPasswordField.setWidthFull();
        confirmPasswordField.setRequiredIndicatorVisible(true);

        Button update = new Button("Update password");
        update.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        update.getStyle().set("margin-top", "var(--lumo-space-s)");
        update.addClickListener(e -> submitPassword(update));

        return card(heading, newPasswordField, confirmPasswordField, update);
    }

    private void submitUsername(Button save) {
        String value = usernameField.getValue();
        if (value == null || value.isBlank()) {
            showError("Please enter a username.");
            return;
        }
        String trimmed = value.trim();
        if (trimmed.equals(currentUser.getUsername())) {
            showError("That's already your username.");
            return;
        }

        save.setEnabled(false);
        try {
            Result<Void> result = presenter.changeUsername(trimmed);
            if (result instanceof Result.Ok<Void>) {
                usernameValue.setText(trimmed);
                showSuccess("Username updated.");
            } else if (result instanceof Result.Fail<Void> fail) {
                usernameField.setValue(currentUser.getUsername());
                showError(fail.message());
            }
        } finally {
            save.setEnabled(true);
        }
    }

    private void submitPassword(Button update) {
        String newPassword = newPasswordField.getValue();
        String confirm = confirmPasswordField.getValue();

        if (newPassword == null || newPassword.isBlank() || confirm == null || confirm.isBlank()) {
            showError("Please fill in both password fields.");
            return;
        }
        if (!newPassword.equals(confirm)) {
            showError("Passwords do not match.");
            return;
        }

        update.setEnabled(false);
        try {
            Result<Void> result = presenter.changePassword(newPassword);
            if (result instanceof Result.Ok<Void>) {
                newPasswordField.clear();
                confirmPasswordField.clear();
                showSuccess("Password updated.");
            } else if (result instanceof Result.Fail<Void> fail) {
                showError(fail.message());
            }
        } finally {
            update.setEnabled(true);
        }
    }

    private Div card(com.vaadin.flow.component.Component... children) {
        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("box-shadow", "var(--lumo-box-shadow-s)")
                .set("padding", "var(--lumo-space-l)");
        card.add(children);
        return card;
    }

    private Span styledLabel(String text) {
        Span label = new Span(text);
        label.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        return label;
    }

    private void showError(String message) {
        Notification n = Notification.show(message);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private void showSuccess(String message) {
        Notification n = Notification.show(message);
        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }
}
