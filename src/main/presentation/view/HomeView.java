package presentation.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import presentation.Brand;
import presentation.CurrentUser;

/**
 * Public landing page mapped to the root route. A bold full-bleed gradient hero
 * showing the brand logo, a tagline, and call-to-action buttons that adapt to
 * the current viewer (guest / active member / suspended member) via
 * {@link CurrentUser}.
 * <p>
 * Intentionally presenter-less: it makes no application-service calls, only
 * reads the UI-layer {@link CurrentUser} session bean (as {@code LoginView} /
 * {@code RegisterView} do), so the MVP "paired presenter" rule — which governs
 * service access — does not apply here.
 * <p>
 * The member quick-links target views still under construction
 * ({@code member/orders} #150, {@code member/notifications} #152); they are
 * wired now and will resolve once those views land.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Ticketanic")
@AnonymousAllowed
public class HomeView extends VerticalLayout {

    private final CurrentUser currentUser;

    public HomeView(CurrentUser currentUser) {
        this.currentUser = currentUser;

        configureHeroBackground();
        add(buildHero());
    }

    private void configureHeroBackground() {
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        setPadding(false);
        setSpacing(false);
        // Deep navy-to-blue gradient that echoes the logo's palette.
        getStyle().set("background",
                "radial-gradient(circle at 50% 30%, #3a6ea5 0%, #233047 55%, #141d2e 100%)");
    }

    private VerticalLayout buildHero() {
        Image logo = Brand.logo("180px");
        logo.getStyle().set("box-shadow", "0 12px 40px rgba(0, 0, 0, 0.45)");

        Paragraph tagline = new Paragraph("Discover and book tickets to live events");
        tagline.getStyle()
                .set("margin", "var(--lumo-space-m) 0 var(--lumo-space-l) 0")
                .set("color", "rgba(255, 255, 255, 0.85)")
                .set("font-size", "var(--lumo-font-size-xl)");

        VerticalLayout hero = new VerticalLayout(logo, tagline);
        hero.setAlignItems(FlexComponent.Alignment.CENTER);
        hero.setSpacing(false);
        hero.setPadding(true);
        hero.setWidth("640px");
        hero.setMaxWidth("90vw");
        hero.getStyle().set("text-align", "center");

        addAudienceContent(hero);
        return hero;
    }

    /** Adds the greeting/notice and the call-to-action button row for the current viewer. */
    private void addAudienceContent(VerticalLayout hero) {
        if (!currentUser.isLoggedIn()) {
            hero.add(buttonRow(
                    ghostNav("Browse events", "events"),
                    ghostNav("Log in", "login"),
                    ghostNav("Register", "register")));
            return;
        }

        if (currentUser.isSuspended()) {
            Paragraph notice = new Paragraph(
                    "Your account is suspended. You can browse events, but cannot purchase "
                            + "tickets or perform other actions until it is reinstated.");
            notice.getStyle()
                    .set("color", "#ffd4d4")
                    .set("max-width", "32rem");
            hero.add(notice, buttonRow(
                    ghostNav("Browse events", "events"),
                    ghostNav("My profile", "member/profile")));
            return;
        }

        H2 welcome = new H2("Welcome back, " + currentUser.getUsername());
        welcome.getStyle()
                .set("margin", "0")
                .set("color", "#ffffff")
                .set("font-size", "var(--lumo-font-size-xl)");
        hero.add(welcome, buttonRow(
                ghostNav("Browse events", "events"),
                ghostNav("My profile", "member/profile"),
                ghostNav("My orders", "member/orders"),
                ghostNav("Notifications", "member/notifications"),
                ghostNav("Open a company", "company/open")));
    }

    private HorizontalLayout buttonRow(Button... buttons) {
        HorizontalLayout row = new HorizontalLayout(buttons);
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        row.getStyle().set("flex-wrap", "wrap").set("margin-top", "var(--lumo-space-m)");
        return row;
    }

    private Button primaryNav(String label, String route) {
        Button button = navButton(label, route);
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        return button;
    }

    /** A translucent outlined button that reads clearly on the dark hero. */
    private Button ghostNav(String label, String route) {
        Button button = navButton(label, route);
        button.addThemeVariants(ButtonVariant.LUMO_LARGE);
        button.getStyle()
                .set("color", "#ffffff")
                .set("background", "rgba(255, 255, 255, 0.10)")
                .set("border", "1px solid rgba(255, 255, 255, 0.55)");
        return button;
    }

    private Button navButton(String label, String route) {
        return new Button(label, e -> getUI().ifPresent(ui -> ui.navigate(route)));
    }
}
