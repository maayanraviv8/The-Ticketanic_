package presentation.view;

import application.dto.SuspensionDTO;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import presentation.CurrentUser;
import presentation.Result;
import presentation.presenter.AdminPresenter;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "admin/members/history", layout = MainLayout.class)
@PageTitle("Suspension History")
public class SuspensionHistoryView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AdminPresenter presenter;
    private final CurrentUser currentUser;
    private VerticalLayout content;

    public SuspensionHistoryView(AdminPresenter presenter, CurrentUser currentUser) {
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

        Tab activeTab = new Tab("Currently Suspended");
        Tab historyTab = new Tab("Full History");
        Tabs tabs = new Tabs(activeTab, historyTab);
        tabs.getStyle().set("padding", "0 2rem");
        add(tabs);

        content = new VerticalLayout();
        content.getStyle().set("padding", "2rem");
        add(content);

        Button back = new Button("Back", e -> UI.getCurrent().navigate("admin/members"));
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        back.getStyle().set("margin", "0 2rem 2rem");
        add(back);

        showSuspensions(true);

        tabs.addSelectedChangeListener(e ->
                showSuspensions(tabs.getSelectedTab() == activeTab));
    }

    private HorizontalLayout buildHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.getStyle()
                .set("background", "linear-gradient(135deg, var(--lumo-primary-color), #141d2e)")
                .set("padding", "2rem")
                .set("width", "100%")
                .set("align-items", "center");
        H2 title = new H2("Suspension History");
        title.getStyle().set("margin", "0").set("color", "white").set("flex", "1");
        header.add(title);
        return header;
    }

    private void showSuspensions(boolean activeOnly) {
        content.removeAll();

        Result<List<SuspensionDTO>> result = presenter.getMemberSuspensions(activeOnly);
        if (result instanceof Result.Fail<List<SuspensionDTO>> fail) {
            content.add(new Paragraph("Error: " + fail.message()));
            return;
        }

        List<SuspensionDTO> suspensions = ((Result.Ok<List<SuspensionDTO>>) result).value();
        if (suspensions.isEmpty()) {
            content.add(new Paragraph(activeOnly
                    ? "No members are currently suspended."
                    : "No suspension records found."));
            return;
        }

        Grid<SuspensionDTO> grid = new Grid<>();
        grid.setItems(suspensions);
        grid.setWidthFull();

        grid.addColumn(dto -> dto.memberEmail)
                .setHeader("Member Email")
                .setSortable(true)
                .setFlexGrow(2);

        grid.addColumn(dto -> dto.suspensionDate != null
                        ? dto.suspensionDate.format(DATE_FMT) : "—")
                .setHeader("Suspended On")
                .setSortable(true)
                .setFlexGrow(1);

        grid.addColumn(dto -> dto.suspensionEndDate != null
                        ? dto.suspensionEndDate.format(DATE_FMT) : "Active")
                .setHeader("Ends On")
                .setFlexGrow(1);

        grid.addColumn(dto -> formatDuration(dto.duration))
                .setHeader("Duration")
                .setFlexGrow(1);

        content.add(grid);
    }

    private String formatDuration(Duration duration) {
        if (duration == null) return "—";
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}