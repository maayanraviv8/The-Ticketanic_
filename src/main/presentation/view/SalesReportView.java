package presentation.view;

import application.dto.SalesReportDTO;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import domain.Aggregates.productioncompany.Permissions;
import presentation.CurrentUser;
import presentation.Result;
import presentation.presenter.SalesReportPresenter;

@Route(value = "company/:companyId/sales", layout = MainLayout.class)
@PageTitle("Sales Report")
public class SalesReportView extends VerticalLayout implements BeforeEnterObserver {

    private final SalesReportPresenter presenter;
    private final CurrentUser currentUser;
    private String companyId;

    public SalesReportView(SalesReportPresenter presenter, CurrentUser currentUser) {
        this.presenter = presenter;
        this.currentUser = currentUser;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = event.getRouteParameters().get("companyId").orElse(null);
        if (companyId == null) {
            event.forwardTo("");
            return;
        }
        if (!currentUser.isLoggedIn() || !currentUser.hasCompanyPermission(companyId, Permissions.GENERATE_SALES_REPORT)) {
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

        H2 title = new H2("Sales Report - " + companyId);
        title.getStyle().set("margin", "0").set("color", "white").set("flex", "1");
        header.add(title);
        add(header);

        VerticalLayout content = new VerticalLayout();
        content.getStyle().set("padding", "1rem 2rem");

        Result<SalesReportDTO> result = presenter.getSalesReport(companyId);
        if (result instanceof Result.Fail<SalesReportDTO> fail) {
            content.add(new Paragraph("Failed to load report: " + fail.message()));
            content.add(backButton());
            add(content);
            return;
        }

        SalesReportDTO report = ((Result.Ok<SalesReportDTO>) result).value();

        VerticalLayout card = new VerticalLayout();
        card.getStyle()
                .set("border", "1px solid #e0e0e0")
                .set("border-radius", "8px")
                .set("padding", "24px")
                .set("box-shadow", "0 2px 4px rgba(0,0,0,0.1)")
                .set("background", "white")
                .set("max-width", "600px");

        card.add(new H3("Summary"));
        card.add(new Paragraph("Total Tickets Sold: " + report.totalTicketsSold));
        card.add(new Paragraph("Total Revenue: $" + String.format("%.2f", report.totalRevenue)));

        if (report.includedManagerIds != null && !report.includedManagerIds.isEmpty()) {
            card.add(new H3("Included Managers"));
            for (String managerId : report.includedManagerIds) {
                Paragraph p = new Paragraph(managerId);
                p.getStyle().set("margin", "0");
                card.add(p);
            }
        }

        content.add(card);
        content.add(backButton());
        add(content);
    }

    private Button backButton() {
        Button back = new Button("Back", e -> UI.getCurrent().navigate("company/" + companyId));
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        return back;
    }
}