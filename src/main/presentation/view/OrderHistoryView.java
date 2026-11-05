package presentation.view;

import application.dto.OrderDTO;
import application.dto.ShowDTO;
import application.dto.TicketDTO;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import presentation.CurrentUser;
import presentation.Result;
import presentation.presenter.OrderHistoryPresenter;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "member/orders", layout = MainLayout.class)
@PageTitle("My Orders | Ticketanic")
@AnonymousAllowed
public class OrderHistoryView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm");

    private final OrderHistoryPresenter presenter;
    private final CurrentUser currentUser;
    private final VerticalLayout content = new VerticalLayout();

    public OrderHistoryView(OrderHistoryPresenter presenter, CurrentUser currentUser) {
        this.presenter = presenter;
        this.currentUser = currentUser;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("background", "var(--lumo-contrast-5pct)");

        H2 title = new H2("My Orders");
        title.getStyle().set("margin-top", "0");

        content.setPadding(false);
        content.setSpacing(true);
        content.setWidthFull();

        add(title, content);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!currentUser.isLoggedIn()) {
            event.forwardTo("login");
            return;
        }
        loadOrders();
    }

    private void loadOrders() {
        content.removeAll();

        Result<List<OrderDTO>> result = presenter.getOrderHistory();
        if (result instanceof Result.Fail<List<OrderDTO>> fail) {
            Notification n = Notification.show(fail.message());
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        List<OrderDTO> orders = ((Result.Ok<List<OrderDTO>>) result).value();
        if (orders.isEmpty()) {
            content.add(buildEmptyState());
            return;
        }

        orders.stream()
                .sorted((a, b) -> b.getPurchaseTime().compareTo(a.getPurchaseTime()))
                .forEach(order -> content.add(buildOrderCard(order)));
    }

    private Div buildOrderCard(OrderDTO order) {
        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("box-shadow", "var(--lumo-box-shadow-s)")
                .set("padding", "var(--lumo-space-l)");

        String eventName = "—";
        Result<String> eventResult = presenter.getEventName(order.getEventId());
        if (eventResult instanceof Result.Ok<String> ok) eventName = ok.value();

        String showDate = "—";
        Result<ShowDTO> showResult = presenter.getShow(order.getShowId());
        if (showResult instanceof Result.Ok<ShowDTO> ok && ok.value().date != null) {
            showDate = ok.value().date.format(DATE_FMT);
        }

        H3 heading = new H3(eventName);
        heading.getStyle().set("margin-top", "0").set("margin-bottom", "var(--lumo-space-s)");

        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "auto 1fr")
                .set("gap", "var(--lumo-space-xs) var(--lumo-space-l)")
                .set("margin-bottom", "var(--lumo-space-m)");

        addGridRow(grid, "Show", showDate);
        addGridRow(grid, "Tickets", String.valueOf(order.getTicketIds() != null ? order.getTicketIds().size() : 0));
        addGridRow(grid, "Total", String.format("$%.2f", order.getFinalPrice()));
        addGridRow(grid, "Purchased", order.getPurchaseTime() != null ? order.getPurchaseTime().format(DATE_FMT) : "—");
        addGridRow(grid, "Order ID", order.getOrderId());

        Details ticketDetails = new Details("Issued tickets", buildTicketList(order));
        ticketDetails.setOpened(false);

        card.add(heading, grid, ticketDetails);
        return card;
    }

    private VerticalLayout buildTicketList(OrderDTO order) {
        VerticalLayout list = new VerticalLayout();
        list.setPadding(false);
        list.setSpacing(false);

        Result<List<TicketDTO>> result = presenter.getTickets(order.getTicketIds());
        if (result instanceof Result.Fail<List<TicketDTO>> fail) {
            Paragraph p = new Paragraph(fail.message());
            p.getStyle().set("color", "var(--lumo-error-text-color)");
            list.add(p);
            return list;
        }

        List<TicketDTO> tickets = ((Result.Ok<List<TicketDTO>>) result).value();
        if (tickets.isEmpty()) {
            list.add(new Paragraph("No ticket details available."));
            return list;
        }

        for (TicketDTO ticket : tickets) {
            String label = ticket.getSeatId() == null
                    ? String.format("Standing · Zone %s · $%.2f",
                            ticket.getZoneId(), ticket.getPrice())
                    : String.format("Zone %s · Seat %s · $%.2f",
                            ticket.getZoneId(), ticket.getSeatId(), ticket.getPrice());
            Span row = new Span(label);
            row.getStyle().set("font-family", "var(--lumo-font-family)");
            list.add(row);
        }
        return list;
    }

    private void addGridRow(Div grid, String labelText, String valueText) {
        Span label = new Span(labelText);
        label.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        grid.add(label, new Span(valueText));
    }

    private Div buildEmptyState() {
        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("box-shadow", "var(--lumo-box-shadow-s)")
                .set("padding", "var(--lumo-space-xl)")
                .set("text-align", "center");

        H3 heading = new H3("No orders yet");
        heading.getStyle().set("margin-top", "0");

        Paragraph p = new Paragraph("Once you complete a purchase, your orders will appear here.");
        p.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Button browseBtn = new Button("Browse events", e -> UI.getCurrent().navigate("events"));
        browseBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        card.add(heading, p, browseBtn);
        return card;
    }
}
