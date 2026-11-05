package presentation.view;

import application.dto.ProductionCompanyDTO;
import application.dto.RoleDTO;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import presentation.*;
import presentation.presenter.AdminPresenter;
import presentation.presenter.HierarchyComponent;

import java.util.List;

@Route(value = "admin/companies", layout = MainLayout.class)
@PageTitle("Company Management")
public class CompanyManagementView extends VerticalLayout implements BeforeEnterObserver {

    private final AdminPresenter presenter;
    private final CurrentUser currentUser;

    private FlexLayout companyCards;
    private List<ProductionCompanyDTO> allCompanies;

    public CompanyManagementView(AdminPresenter presenter, CurrentUser currentUser) {
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

        HorizontalLayout header = new HorizontalLayout();
        header.getStyle()
                .set("background", "linear-gradient(135deg, var(--lumo-primary-color), #141d2e)")
                .set("padding", "2rem")
                .set("width", "100%")
                .set("align-items", "center");
        H2 title = new H2("Company Management");
        title.getStyle().set("margin", "0").set("color", "white");
        header.add(title);
        add(header);

        Result<List<ProductionCompanyDTO>> result = presenter.getAllCompanies();
        if (result instanceof Result.Fail<List<ProductionCompanyDTO>> fail) {
            Notification.show(fail.message());
            return;
        }
        allCompanies = ((Result.Ok<List<ProductionCompanyDTO>>) result).value();

        Tab activeTab = new Tab("Active");
        Tab closedTab = new Tab("Closed");
        Tabs tabs = new Tabs(activeTab, closedTab);
        tabs.getStyle().set("padding", "0 2rem");
        add(tabs);

        companyCards = new FlexLayout();
        companyCards.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        companyCards.getStyle().set("gap", "1.5rem").set("padding", "2rem");
        add(companyCards);

        Button back = new Button("Back", e -> UI.getCurrent().navigate("admin"));
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        add(back);

        showCompanies(true);

        tabs.addSelectedChangeListener(e -> showCompanies(tabs.getSelectedTab() == activeTab));
    }

    private void showCompanies(boolean active) {
        companyCards.removeAll();
        allCompanies.stream()
                .filter(c -> c.isActive == active)
                .forEach(c -> companyCards.add(buildCompanyCard(c)));
        if (companyCards.getComponentCount() == 0) {
            companyCards.add(new Paragraph("No " + (active ? "active" : "closed") + " companies."));
        }
    }

    private VerticalLayout buildCompanyCard(ProductionCompanyDTO company) {
        VerticalLayout card = new VerticalLayout();
        card.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "1.5rem")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.08)")
                .set("background", "white")
                .set("width", "320px");

        H3 name = new H3(company.name);
        name.getStyle().set("margin", "0");

        Paragraph status = new Paragraph(company.isActive ? "Active" : "Closed");
        status.getStyle().set("color", company.isActive
                ? "var(--lumo-success-color)"
                : "var(--lumo-error-color)");

        HorizontalLayout buttons = new HorizontalLayout();

        if (company.isActive) {
            Button closeBtn = new Button("Close Company", e -> {
                Result<Void> res = presenter.closeCompany(company.name);
                if (res instanceof Result.Ok) {
                    Notification.show("Company closed successfully");
                    loadPage();
                } else {
                    Notification.show(((Result.Fail<Void>) res).message());
                }
            });
            closeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            buttons.add(closeBtn);
        }

        Button hierarchyBtn = new Button("View Hierarchy", e -> openHierarchyDialog(company.name));
        hierarchyBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);
        buttons.add(hierarchyBtn);

        card.add(name, status, buttons);
        return card;
    }

    private void openHierarchyDialog(String companyId) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Hierarchy - " + companyId);
        dialog.setWidth("500px");

        VerticalLayout content = new VerticalLayout();

        Result<List<RoleDTO>> result = presenter.getCompanyHierarchy(companyId);
        if (result instanceof Result.Fail<List<RoleDTO>> fail) {
            content.add(new Paragraph("Error: " + fail.message()));
        } else {
            List<RoleDTO> roles = ((Result.Ok<List<RoleDTO>>) result).value();
            content.add(HierarchyComponent.buildTree(roles));
        }

        Button closeBtn = new Button("Close", e -> dialog.close());
        dialog.getFooter().add(closeBtn);
        dialog.add(content);
        dialog.open();
    }
}