package presentation.view;

import application.interfaces.INotificationService;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.RouterLink;
import presentation.Brand;
import presentation.CurrentUser;
import presentation.presenter.LogoutPresenter;

public class MainLayout extends AppLayout implements AfterNavigationObserver {

    private final CurrentUser currentUser;
    private final LogoutPresenter logoutPresenter;
    private final INotificationService notificationService;
    private final SideNav sideNav = new SideNav();
    private final Button logoutButton = createLogoutButton();
    private final Div resumeBanner = new Div();
    private String currentPath = "";

    public MainLayout(CurrentUser currentUser, LogoutPresenter logoutPresenter,
                      INotificationService notificationService) {
        this.currentUser = currentUser;
        this.logoutPresenter = logoutPresenter;
        this.notificationService = notificationService;

        VerticalLayout topStack = new VerticalLayout();
        topStack.setSpacing(false);
        topStack.setPadding(false);
        topStack.setWidthFull();
        topStack.add(createHeader(), buildResumeBanner());
        addToNavbar(topStack);

        addToDrawer(sideNav);
        getElement().appendChild(logoutButton.getElement());
        refreshNav();
    }

    private HorizontalLayout createHeader() {
        DrawerToggle toggle = new DrawerToggle();
        // Keep the toggle visible against the dark header bar.
        toggle.getStyle().set("color", "var(--lumo-base-color)");

        // The logo image carries the "Ticketanic" wordmark; clicking it returns
        // to the homepage from any view.
        RouterLink homeLink = new RouterLink();
        homeLink.setRoute(HomeView.class);
        homeLink.add(Brand.logo("64px"));
        homeLink.getStyle()
                .set("text-decoration", "none")
                .set("display", "inline-flex")
                .set("align-items", "center");

        HorizontalLayout header = new HorizontalLayout(toggle, homeLink);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.expand(homeLink);
        header.setWidthFull();
        header.setPadding(true);
        // Match the logo's own background color (sampled from its corners) so the
        // baked-in dark square blends into the bar instead of reading as a tile.
        header.getStyle().set("background", "#374856");
        return header;
    }

    private Div buildResumeBanner() {
        Span message = new Span("You have a reservation in progress.");
        message.getStyle().set("font-weight", "500");

        Button resumeButton = new Button("Resume");
        resumeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        resumeButton.addClickListener(e -> {
            String id = currentUser.getActiveOrderId();
            if (id != null) {
                getUI().ifPresent(ui -> ui.navigate("purchase/" + id));
            }
        });

        HorizontalLayout row = new HorizontalLayout(message, resumeButton);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        row.setWidthFull();
        row.setPadding(false);

        resumeBanner.add(row);
        resumeBanner.setWidthFull();
        resumeBanner.getStyle()
                .set("background", "var(--lumo-warning-color-10pct)")
                .set("color", "var(--lumo-warning-text-color)")
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-m)")
                .set("border-bottom", "1px solid var(--lumo-warning-color-50pct)");
        resumeBanner.setVisible(false);
        return resumeBanner;
    }

    private Button createLogoutButton() {
        Button button = new Button("Logout");
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        button.getStyle()
                .set("position", "fixed")
                .set("bottom", "16px")
                .set("left", "16px")
                .set("z-index", "100");
        button.addClickListener(e -> openLogoutConfirmation());
        return button;
    }

    private void openLogoutConfirmation() {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Log out?");
        dialog.setText("Are you sure you want to log out of Ticketanic?");
        dialog.setCancelable(true);
        dialog.setConfirmText("Log out");
        dialog.addConfirmListener(e -> performLogout());
        dialog.open();
    }

    private void performLogout() {
        logoutPresenter.logout();
        Notification.show(
                "You have been logged out. Any active order remains saved to your account.",
                4000,
                Notification.Position.BOTTOM_CENTER);
        // setLocation does an atomic browser navigation to "/". A prior
        // ui.navigate("") + ui.getPage().reload() pair raced: reload fired
        // before the queued navigate updated the URL, so the browser reloaded
        // the previous (often auth-gated) route and bounced to /login.
        getUI().ifPresent(ui -> ui.getPage().setLocation("/"));
    }

    private void refreshNav() {
        sideNav.removeAll();
        sideNav.addItem(new SideNavItem("Browse events", "events"));

        logoutButton.setVisible(currentUser.isLoggedIn());
        refreshResumeBanner();

        if (!currentUser.isLoggedIn()) {
            sideNav.addItem(new SideNavItem("Login", "login"));
            sideNav.addItem(new SideNavItem("Register", "register"));
            return;
        }

        sideNav.addItem(new SideNavItem("My profile", "member/profile"));
        sideNav.addItem(new SideNavItem("My orders", "member/orders"));
        sideNav.addItem(buildNotificationsNavItem());

        if (currentUser.isSuspended()) {
            return;
        }

        for (String companyId : currentUser.getCompaniesWithRoles()) {
            sideNav.addItem(new SideNavItem("Company: " + companyId, "company/" + companyId));
        }

        sideNav.addItem(new SideNavItem("Open a company", "company/open"));

        if (currentUser.isAdmin()) {
            sideNav.addItem(new SideNavItem("Admin", "admin"));
        }
    }

    private SideNavItem buildNotificationsNavItem() {
        SideNavItem item = new SideNavItem("Notifications", "member/notifications");
        try {
            int count = notificationService.getPendingNotifications(currentUser.getEmail()).size();
            if (count > 0) {
                Span badge = new Span(String.valueOf(count));
                badge.getStyle()
                        .set("background", "var(--lumo-error-color)")
                        .set("color", "var(--lumo-base-color)")
                        .set("border-radius", "var(--lumo-border-radius-l)")
                        .set("padding", "0 6px")
                        .set("font-size", "var(--lumo-font-size-xs)")
                        .set("font-weight", "600")
                        .set("min-width", "18px")
                        .set("text-align", "center");
                item.setSuffixComponent(badge);
            }
        } catch (Exception ignored) {
        }
        return item;
    }

    private void refreshResumeBanner() {
        String activeOrderId = currentUser.getActiveOrderId();
        boolean onMatchingPurchase = activeOrderId != null
                && currentPath.equals("purchase/" + activeOrderId);
        resumeBanner.setVisible(
                currentUser.isLoggedIn()
                        && activeOrderId != null
                        && !onMatchingPurchase);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        currentPath = event.getLocation().getPath();
        refreshNav();
    }
}
