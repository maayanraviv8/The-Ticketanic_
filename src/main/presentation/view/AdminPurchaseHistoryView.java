package presentation.view;

import application.dto.OrderDTO;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import presentation.*;
import presentation.presenter.AdminPresenter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Route(value = "admin/history", layout = MainLayout.class)
@PageTitle("Purchase History")
public class AdminPurchaseHistoryView extends VerticalLayout implements BeforeEnterObserver {

    private final AdminPresenter presenter;
    private final CurrentUser currentUser;
    private final DatePicker startDatePicker = new DatePicker("Start Date");
    private final DatePicker endDatePicker   = new DatePicker("End Date");
    private VerticalLayout content;

    public AdminPurchaseHistoryView(AdminPresenter presenter, CurrentUser currentUser) {
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

        Tab byBuyers = new Tab("By Buyers");
        Tab byCompanies = new Tab("By Production Companies");
        Tabs tabs = new Tabs(byBuyers, byCompanies);
        tabs.getStyle().set("padding", "0 2rem");
        add(tabs);

        startDatePicker.setClearButtonVisible(true);
        endDatePicker.setClearButtonVisible(true);
        HorizontalLayout dateRow = new HorizontalLayout(startDatePicker, endDatePicker);
        dateRow.getStyle().set("padding", "0.5rem 2rem");
        add(dateRow);

        content = new VerticalLayout();
        content.getStyle().set("padding", "2rem");
        add(content);

        Button back = new Button("Back", e -> UI.getCurrent().navigate("admin"));
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        back.getStyle().set("margin", "0 2rem 2rem");
        add(back);

        showByBuyers();

        tabs.addSelectedChangeListener(e -> {
            if (tabs.getSelectedTab() == byBuyers) showByBuyers();
            else showByCompanies();
        });

        startDatePicker.addValueChangeListener(e -> {
            if (tabs.getSelectedTab() == byBuyers) showByBuyers();
            else showByCompanies();
        });
        endDatePicker.addValueChangeListener(e -> {
            if (tabs.getSelectedTab() == byBuyers) showByBuyers();
            else showByCompanies();
        });
    }

    private HorizontalLayout buildHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.getStyle()
                .set("background", "linear-gradient(135deg, var(--lumo-primary-color), #141d2e)")
                .set("padding", "2rem")
                .set("width", "100%")
                .set("align-items", "center");
        H2 title = new H2("Purchase History");
        title.getStyle().set("margin", "0").set("color", "white").set("flex", "1");
        header.add(title);
        return header;
    }

    private void showByBuyers() {
        content.removeAll();
        Result<Map<String, List<OrderDTO>>> result = presenter.getPurchaseHistoryByBuyers(startDate(), endDate());
        if (result instanceof Result.Fail<Map<String, List<OrderDTO>>> fail) {
            content.add(new Paragraph(fail.message()));
        } else {
            content.add(PurchaseHistoryComponent.buildHistory(
                    ((Result.Ok<Map<String, List<OrderDTO>>>) result).value()));
        }
    }

    private void showByCompanies() {
        content.removeAll();
        Result<Map<String, Map<String, List<OrderDTO>>>> result = presenter.getPurchaseHistoryByCompanies(startDate(), endDate());
        if (result instanceof Result.Fail<Map<String, Map<String, List<OrderDTO>>>> fail) {
            content.add(new Paragraph(fail.message()));
        } else {
            content.add(PurchaseHistoryComponent.buildNestedHistory(
                    ((Result.Ok<Map<String, Map<String, List<OrderDTO>>>>) result).value()));
        }
    }

    private LocalDateTime startDate() {
        LocalDate d = startDatePicker.getValue();
        return d != null ? d.atStartOfDay() : null;
    }

    private LocalDateTime endDate() {
        LocalDate d = endDatePicker.getValue();
        return d != null ? d.atTime(23, 59, 59) : null;
    }
}