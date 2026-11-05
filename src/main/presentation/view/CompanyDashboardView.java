package presentation.view;

import application.dto.EventDTO;
import application.dto.OrderDTO;
import application.dto.ProductionCompanyDTO;
import application.dto.RoleDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.details.DetailsVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import domain.Aggregates.productioncompany.Permissions;
import presentation.*;
import presentation.presenter.CompanyDashboardPresenter;
import presentation.presenter.EventManagementPresenter;
import presentation.presenter.HierarchyComponent;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Route(value = "company/:companyId", layout = MainLayout.class)
@PageTitle("Company Dashboard")
@AnonymousAllowed
public class CompanyDashboardView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

    private final CompanyDashboardPresenter presenter;
    private final EventManagementPresenter eventPresenter;
    private final CurrentUser currentUser;
    private String companyId;

    public CompanyDashboardView(CompanyDashboardPresenter presenter, EventManagementPresenter eventPresenter, CurrentUser currentUser) {
        this.presenter = presenter;
        this.eventPresenter = eventPresenter;
        this.currentUser = currentUser;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = event.getRouteParameters().get("companyId").orElse(null);
        if (companyId == null) {
            event.forwardTo("events");
            return;
        }
        presenter.refreshUserRoles();
        loadPage();
    }

    private void loadPage() {
        removeAll();
        Result<ProductionCompanyDTO> companyResult = presenter.getCompanyInfo(companyId);
        if (companyResult instanceof Result.Fail<ProductionCompanyDTO> fail) {
            Notification.show(fail.message());
            return;
        }
        ProductionCompanyDTO company = ((Result.Ok<ProductionCompanyDTO>) companyResult).value();

        add(buildHeader(company));

        if (!company.isActive) {
            if (!canViewInactiveCompany()) {
                add(new Paragraph("This company is not currently active."));
                return;
            }
            buildFounderSection(company);
            Details reports = buildReportsSection();
            if (reports != null) {
                HorizontalLayout hl = new HorizontalLayout(reports);
                hl.getStyle().set("padding", "1rem 2rem");
                add(hl);
            }
            buildOwnerSection();
        } else {
            if (currentUser.isLoggedIn() && currentUser.hasAnyRoleAt(companyId)) {
                add(buildActionButtons());
            }
            buildFounderSection(company);
            buildOwnerSection();
        }

        Result<List<EventDTO>> eventsResult = presenter.getCompanyEvents(companyId);
        if (eventsResult instanceof Result.Fail<List<EventDTO>> fail) {
            Notification.show(fail.message());
        } else {
            add(buildEventsSection(((Result.Ok<List<EventDTO>>) eventsResult).value()));
        }
    }

    private VerticalLayout buildHeader(ProductionCompanyDTO company) {
        VerticalLayout header = new VerticalLayout();
        header.getStyle()
                .set("background", "linear-gradient(135deg, var(--lumo-primary-color), #141d2e)")
                .set("padding", "2rem")
                .set("color", "white");

        H2 title = new H2(company.name);
        title.getStyle().set("margin", "0").set("color", "white");
        header.add(title);

        if (!company.isActive) {
            Paragraph inactive = new Paragraph("This company is currently inactive");
            inactive.getStyle().set("color", "var(--lumo-error-color)");
            header.add(inactive);
        }
        return header;
    }

    private HorizontalLayout buildActionButtons() {
        HorizontalLayout actions = new HorizontalLayout();
        actions.getStyle()
                .set("padding", "1rem 2rem")
                .set("flex-wrap", "wrap")
                .set("gap", "1.5rem");

        Details eventsSection = buildManageEventsSection();
        if (eventsSection != null) {
            actions.add(eventsSection);
        }

        Details appointmentsSection = buildAppointmentsSection();
        if (appointmentsSection != null) {
            actions.add(appointmentsSection);
        }

        Details policiesSection = buildPoliciesSection();
        if (policiesSection != null) {
            actions.add(policiesSection);
        }

        Details reportsSection = buildReportsSection();
        if (reportsSection != null) {
            actions.add(reportsSection);
        }

        return actions;
    }

    private Details buildAppointmentsSection() {
        HorizontalLayout content = new HorizontalLayout();
        content.getStyle().set("padding", "0.5rem 0").set("gap", "1rem");

        boolean canManageManagers = currentUser.hasCompanyPermission(companyId, Permissions.APPOINT_MANAGERS);
        boolean canManageOwners = currentUser.hasCompanyPermission(companyId, Permissions.APPOINT_OWNERS);

        if (canManageManagers || canManageOwners) {
            content.add(createButton("Manage Roles & Appointments", "company/" + companyId + "/roles", ButtonVariant.LUMO_PRIMARY));
        }

        if (content.getComponentCount() == 0) {
            return null;
        }

        Details details = new Details("Appointments Management", content);
        details.addThemeVariants(DetailsVariant.FILLED);
        return details;
    }

    private Details buildPoliciesSection() {
        HorizontalLayout content = new HorizontalLayout();
        content.getStyle().set("padding", "0.5rem 0").set("gap", "1rem");

        if (currentUser.hasCompanyPermission(companyId, Permissions.CHANGE_PURCHASE_POLICY)) {
            content.add(createButton("Purchase Policy", "company/" + companyId + "/purchase-policy", ButtonVariant.LUMO_CONTRAST));
        }

        if (currentUser.hasCompanyPermission(companyId, Permissions.CHANGE_DISCOUNT_POLICY)) {
            content.add(createButton("Discount Policy", "company/" + companyId + "/discount-policy", ButtonVariant.LUMO_CONTRAST));
        }

        if (content.getComponentCount() == 0) {
            return null;
        }

        Details details = new Details("Policies Management", content);
        details.addThemeVariants(DetailsVariant.FILLED);
        return details;
    }

    private Details buildReportsSection() {
        HorizontalLayout content = new HorizontalLayout();
        content.getStyle().set("padding", "0.5rem 0").set("gap", "1rem");

        if (currentUser.hasCompanyPermission(companyId, Permissions.VIEW_PURCHASE_HISTORY)) {
            Button purchaseBtn = new Button("Purchase History", e -> openPurchaseHistoryDialog());
            purchaseBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
            content.add(purchaseBtn);
        }

        if (currentUser.hasCompanyPermission(companyId, Permissions.GENERATE_SALES_REPORT)) {
            content.add(createButton("Sales Report", "company/" + companyId + "/sales", ButtonVariant.LUMO_CONTRAST));
        }

        if (currentUser.hasCompanyPermission(companyId, Permissions.VIEW_MANAGEMENT_HIERARCHY)) {
            Button hierarchyBtn = new Button("Management Hierarchy", e -> openHierarchyDialog());
            hierarchyBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
            content.add(hierarchyBtn);
        }

        if (content.getComponentCount() == 0) {
            return null;
        }

        Details details = new Details("Reports & History", content);
        details.addThemeVariants(DetailsVariant.FILLED);
        return details;
    }



    private Button createButton(String label, String route, ButtonVariant variant) {
        Button btn = new Button(label, e -> getUI().ifPresent(ui -> ui.navigate(route)));
        btn.addThemeVariants(variant);
        return btn;
    }

    private VerticalLayout buildEventsSection(List<EventDTO> events) {
        VerticalLayout section = new VerticalLayout();
        section.getStyle().set("padding", "1rem 2rem");
        section.add(new H3("Events"));

        if (events.isEmpty()) {
            section.add(new Paragraph("No events yet."));
            return section;
        }

        FlexLayout cards = new FlexLayout();
        cards.getStyle().set("flex-wrap", "wrap").set("gap", "1rem");
        events.forEach(ev -> cards.add(buildEventCard(ev)));
        section.add(cards);
        return section;
    }

    private Details buildManageEventsSection() {
        HorizontalLayout content = new HorizontalLayout();

        if (currentUser.hasCompanyPermission(companyId, Permissions.EVENT_EDITING)) {
            Button createEventBtn = new Button("Create Event", e ->
                    EventManagementView.openEventDialog(null, companyId, eventPresenter, () -> {}, currentUser));
            createEventBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            content.add(createEventBtn);
        }

        if (currentUser.hasCompanyPermission(companyId, Permissions.EVENT_EDITING)
                || currentUser.hasCompanyPermission(companyId, Permissions.EDIT_VENUE_LAYOUT)
                || currentUser.hasCompanyPermission(companyId, Permissions.CHANGE_PURCHASE_POLICY)
                || currentUser.hasCompanyPermission(companyId, Permissions.CHANGE_DISCOUNT_POLICY)) {
            Button manageBtn = createButton("Manage Existing Events",
                    "company/" + companyId + "/events", ButtonVariant.LUMO_CONTRAST);
            content.add(manageBtn);
        }

        if (content.getComponentCount() == 0) return null;

        Details section = new Details("Events Management", content);
        section.addThemeVariants(DetailsVariant.FILLED);
        return section;
    }

    private VerticalLayout buildEventCard(EventDTO event) {
        VerticalLayout card = new VerticalLayout();
        card.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "1rem")
                .set("min-width", "300px")
                .set("max-width", "400px")
                .set("flex", "1");

        H3 name = new H3(event.name);
        name.getStyle().set("margin", "0");

        Paragraph date = new Paragraph(event.startDate.format(DATE_FMT));
        date.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Button viewBtn = new Button("View Details",
                e -> getUI().ifPresent(ui -> ui.navigate("event/" + event.eventId)));
        viewBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        viewBtn.getStyle().set("margin-top", "auto");

        card.add(name, date, viewBtn);
        return card;
    }

    private void openHierarchyDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Management Hierarchy - " + companyId);
        dialog.setWidth("500px");
        Result<java.util.Map<String, java.util.List<String>>> result = presenter.getCompanyHierarchy(companyId);
        if (result instanceof Result.Fail<java.util.Map<String, java.util.List<String>>> fail) {
            dialog.add(new Paragraph(fail.message()));
        } else {
            dialog.add(HierarchyComponent.buildTree(((Result.Ok<java.util.Map<String, java.util.List<String>>>) result).value()));
        }
        dialog.getFooter().add(new Button("Close", e -> dialog.close()));
        dialog.open();
    }

    private boolean canViewInactiveCompany() {
        if (currentUser.isAdmin()) return true;
        RoleDTO role = currentUser.getRolesByCompany().get(companyId);
        if (role == null) return false;
        return role.roleType.equals("Founder") || role.roleType.equals("Owner");
    }

    private void buildFounderSection(ProductionCompanyDTO company) {
        HorizontalLayout founderActions = new HorizontalLayout();
        founderActions.getStyle().set("padding", "0 2rem");

        RoleDTO myRole = currentUser.getRolesByCompany().get(companyId);
        boolean isFounder = myRole != null && "Founder".equals(myRole.roleType);
        if (!isFounder) return ;


        if (company.isActive) {
            Button closeBtn = new Button("Close Company", e -> {
                Result<Void> result = presenter.closeCompany(companyId);
                if (result instanceof Result.Ok) {
                    Notification.show("Company closed successfully");
                    loadPage();
                } else {
                    Notification.show(((Result.Fail<Void>) result).message());
                }
            });
            closeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
            founderActions.add(closeBtn);
        } else {
            Button reopenBtn = new Button("Reopen Company", e -> {
                Result<Void> result = presenter.reopenCompany(companyId);
                if (result instanceof Result.Ok) {
                    Notification.show("Company reopened successfully");
                    loadPage();
                } else {
                    Notification.show(((Result.Fail<Void>) result).message());
                }
            });
            reopenBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
            founderActions.add(reopenBtn);
        }
        add(founderActions);
    }

    private void openPurchaseHistoryDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Purchase History - " + companyId);
        dialog.setWidth("600px");
        Result<Map<String, List<OrderDTO>>> result = presenter.getCompanyPurchaseHistory(companyId);
        if (result instanceof Result.Fail<Map<String, List<OrderDTO>>> fail) {
            dialog.add(new Paragraph(fail.message()));
        } else {
            dialog.add(PurchaseHistoryComponent.buildHistory(((Result.Ok<Map<String, List<OrderDTO>>>) result).value()));
        }
        dialog.getFooter().add(new Button("Close", e -> dialog.close()));
        dialog.open();
    }

    private void buildOwnerSection() {
        RoleDTO myRole = currentUser.getRolesByCompany().get(companyId);
        boolean isOwner = myRole != null && "Owner".equals(myRole.roleType);
        if (!isOwner) return;

        HorizontalLayout ownerActions = new HorizontalLayout();
        ownerActions.getStyle().set("padding", "0 2rem");

        Button relinquishBtn = new Button("Relinquish Ownership", e -> {
            Result<Void> result = presenter.relinquishOwnership(companyId);
            if (result instanceof Result.Ok) {
                Notification.show("Ownership relinquished successfully");
                getUI().ifPresent(ui -> ui.navigate("events"));
            } else {
                Notification.show(((Result.Fail<Void>) result).message());
            }
        });
        relinquishBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        ownerActions.add(relinquishBtn);
        add(ownerActions);
    }
}