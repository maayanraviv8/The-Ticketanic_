package presentation.view;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import presentation.Brand;
import presentation.CurrentUser;
import presentation.Result;
import presentation.presenter.RegisterPresenter;

@Route(value = "register", layout = MainLayout.class)
@PageTitle("Register | Ticketanic")
@AnonymousAllowed
public class RegisterView extends VerticalLayout implements BeforeEnterObserver {

    private final RegisterPresenter presenter;
    private final CurrentUser currentUser;

    private final TextField usernameField = new TextField("Username");
    private final EmailField emailField = new EmailField("Email");
    private final PasswordField passwordField = new PasswordField("Password");
    private final PasswordField confirmPasswordField = new PasswordField("Confirm password");
    private final Button registerButton = new Button("Create account");

    public RegisterView(RegisterPresenter presenter, CurrentUser currentUser) {
        this.presenter = presenter;
        this.currentUser = currentUser;

        configurePageBackground();
        add(buildCard());
    }

    private void configurePageBackground() {
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        setPadding(false);
        setSpacing(false);
        getStyle().set("background", "var(--lumo-contrast-5pct)");
    }

    private VerticalLayout buildCard() {
        Image logo = Brand.logo("88px");

        Paragraph subtitle = new Paragraph("Create your account");
        subtitle.getStyle()
                .set("margin", "0 0 var(--lumo-space-m) 0")
                .set("color", "var(--lumo-secondary-text-color)");

        usernameField.setRequiredIndicatorVisible(true);
        usernameField.setWidthFull();

        emailField.setRequiredIndicatorVisible(true);
        emailField.setWidthFull();
        emailField.setPlaceholder("you@example.com");

        passwordField.setRequiredIndicatorVisible(true);
        passwordField.setWidthFull();

        confirmPasswordField.setRequiredIndicatorVisible(true);
        confirmPasswordField.setWidthFull();

        registerButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        registerButton.addClickShortcut(Key.ENTER);
        registerButton.addClickListener(e -> submit());
        registerButton.setWidthFull();
        registerButton.getStyle().set("margin-top", "var(--lumo-space-s)");

        RouterLink loginLink = new RouterLink("Already have an account? Log in", LoginView.class);
        loginLink.getStyle()
                .set("margin-top", "var(--lumo-space-s)")
                .set("font-size", "var(--lumo-font-size-s)");

        VerticalLayout card = new VerticalLayout(
                logo, subtitle, usernameField, emailField, passwordField, confirmPasswordField, registerButton, loginLink);
        card.setAlignItems(FlexComponent.Alignment.STRETCH);
        card.setSpacing(false);
        card.setPadding(true);
        card.setWidth("360px");
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("box-shadow", "var(--lumo-box-shadow-m)")
                .set("padding", "var(--lumo-space-l)")
                .set("gap", "var(--lumo-space-s)");

        card.setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, logo);
        card.setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, subtitle);
        card.setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, loginLink);

        return card;
    }

    private void submit() {
        String username = usernameField.getValue();
        String email = emailField.getValue();
        String password = passwordField.getValue();
        String confirmPassword = confirmPasswordField.getValue();

        if (username == null || username.isBlank()
                || email == null || email.isBlank()
                || password == null || password.isBlank()
                || confirmPassword == null || confirmPassword.isBlank()) {
            showError("Please fill in every field");
            return;
        }
        if (emailField.isInvalid()) {
            showError("Please enter a valid email address");
            return;
        }
        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match");
            return;
        }

        Result<Void> result = presenter.register(username, email, password);
        if (result instanceof Result.Ok<Void>) {
            Notification success = Notification.show("Account created — please log in");
            success.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            getUI().ifPresent(ui -> ui.navigate("login"));
        } else if (result instanceof Result.Fail<Void> fail) {
            showError(fail.message());
        }
    }

    private void showError(String message) {
        Notification n = Notification.show(message);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (currentUser.isLoggedIn()) {
            event.forwardTo("");
        }
    }
}
