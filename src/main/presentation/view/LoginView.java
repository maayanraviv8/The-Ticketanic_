package presentation.view;

import application.dto.LoginContextDTO;
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
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import presentation.*;
import presentation.presenter.LoginPresenter;

@Route(value = "login", layout = MainLayout.class)
@PageTitle("Log in | Ticketanic")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginPresenter presenter;
    private final CurrentUser currentUser;

    private final EmailField emailField = new EmailField("Email");
    private final PasswordField passwordField = new PasswordField("Password");
    private final Button loginButton = new Button("Log in");

    public LoginView(LoginPresenter presenter, CurrentUser currentUser) {
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

        Paragraph subtitle = new Paragraph("Log in to continue");
        subtitle.getStyle()
                .set("margin", "0 0 var(--lumo-space-m) 0")
                .set("color", "var(--lumo-secondary-text-color)");

        emailField.setRequiredIndicatorVisible(true);
        emailField.setWidthFull();
        emailField.setPlaceholder("you@example.com");

        passwordField.setRequiredIndicatorVisible(true);
        passwordField.setWidthFull();

        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loginButton.addClickShortcut(com.vaadin.flow.component.Key.ENTER);
        loginButton.addClickListener(e -> submit());
        loginButton.setWidthFull();
        loginButton.getStyle().set("margin-top", "var(--lumo-space-s)");

        RouterLink registerLink = new RouterLink("No account yet? Register", RegisterView.class);
        registerLink.getStyle()
                .set("margin-top", "var(--lumo-space-s)")
                .set("font-size", "var(--lumo-font-size-s)");

        VerticalLayout card = new VerticalLayout(logo, subtitle, emailField, passwordField, loginButton, registerLink);
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

        // Center the logo and register link inside the stretched column.
        card.setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, registerLink);
        card.setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, logo);
        card.setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, subtitle);

        return card;
    }

    private void submit() {
        String email = emailField.getValue();
        String password = passwordField.getValue();

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            showError("Please enter both email and password");
            return;
        }

        Result<LoginContextDTO> result = presenter.login(email, password);
        if (result instanceof Result.Ok<application.dto.LoginContextDTO> ok) {
            Notification success = Notification.show("Welcome, " + ok.value().getMember().getUsername());
            success.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            // HomeView is the member landing page: it greets the member and links to
            // orders, notifications, profile, and event browsing.
            getUI().ifPresent(ui -> ui.navigate(""));
        } else if (result instanceof Result.Fail<application.dto.LoginContextDTO> fail) {
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
