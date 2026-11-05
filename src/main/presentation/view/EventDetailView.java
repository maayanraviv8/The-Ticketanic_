package presentation.view;

import application.dto.ActiveOrderDTO;
import application.dto.EventDTO;
import application.dto.QueueStatusDTO;
import application.dto.RaffleStatusDTO;
import application.dto.ShowDTO;
import application.dto.SeatDTO;
import application.dto.VenueMapDTO;
import application.dto.ZoneDTO;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.shared.Registration;
import presentation.*;
import presentation.presenter.EventDetailPresenter;
import presentation.presenter.PurchasePresenter;
import presentation.presenter.VirtualQueuePresenter;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Route(value = "event/:eventId", layout = MainLayout.class)
@PageTitle("Event Details | Ticketanic")
@AnonymousAllowed
public class EventDetailView extends VerticalLayout implements BeforeEnterObserver {

    private final EventDetailPresenter presenter;
    private final PurchasePresenter purchasePresenter;
    private final VirtualQueuePresenter queuePresenter;
    private final CurrentUser currentUser;

    // Raffle state — for raffle events, winning (not the queue) gates seat selection
    private boolean isRaffleEvent = false;
    private String winnerRaffleCode = null;
    private String publicRaffleStatus = null; // event-level "OPEN"/"DRAWN", visible to guests too

    private final VerticalLayout eventDetailsSection = new VerticalLayout();
    private final VerticalLayout showsSection = new VerticalLayout();
    private final VerticalLayout showMapSection = new VerticalLayout();

    // Queue state — tracks any queue slot we currently hold (allowed or waiting)
    private Registration queuePollListener;
    private String joinedShowId;          // any queue slot the user holds (allowed OR waiting)
    private String waitingOnShowId;       // non-null only while in the waiting state
    private Span waitingPositionLabel;

    // Selection state — reset each time a new show map is loaded
    private String currentShowId = null;
    private String currentZoneId = null;
    private final Set<String> selectedSeatIds = new LinkedHashSet<>();
    private final Map<String, Div> selectedDotElements = new HashMap<>();
    private final List<IntegerField> standingQtyFields = new ArrayList<>();
    private boolean clearingSelection = false;
    private int standingQty = 0;
    private double zonePricePerTicket = 0;

    // Non-null when the user already has an active order for the event they are viewing.
    // When set, the view operates in "edit" mode: the seat map adds to this order
    // instead of creating a new one.
    private ActiveOrderDTO existingOrder;

    // Reserve bar — visible only when something is selected
    private final Div reserveBar = new Div();
    private final Span reserveBarLabel = new Span();
    private final Button reserveBtn = new Button("Reserve Tickets");

    public EventDetailView(EventDetailPresenter presenter,
                           PurchasePresenter purchasePresenter,
                           VirtualQueuePresenter queuePresenter,
                           CurrentUser currentUser) {
        this.presenter = presenter;
        this.purchasePresenter = purchasePresenter;
        this.queuePresenter = queuePresenter;
        this.currentUser = currentUser;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("background", "var(--lumo-contrast-5pct)");

        Button backButton = new Button("← Back to Events", e -> UI.getCurrent().navigate("events"));
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        eventDetailsSection.setPadding(false);
        showsSection.setPadding(false);
        showMapSection.setPadding(false);

        buildReserveBar();

        add(backButton, eventDetailsSection, showsSection, showMapSection, reserveBar);
    }

    private void buildReserveBar() {
        reserveBar.setWidthFull();
        reserveBar.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-top", "2px solid var(--lumo-primary-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("box-shadow", "var(--lumo-box-shadow-m)")
                .set("padding", "var(--lumo-space-m) var(--lumo-space-l)")
                .set("display", "none")  // hidden until selection is made
                .set("align-items", "center")
                .set("justify-content", "space-between");

        reserveBarLabel.getStyle().set("font-size", "var(--lumo-font-size-m)");

        reserveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        reserveBtn.addClickListener(e -> onReserveClicked());

        HorizontalLayout row = new HorizontalLayout(reserveBarLabel, reserveBtn);
        row.setWidthFull();
        row.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        row.setPadding(false);

        reserveBar.add(row);
    }

    private void onReserveClicked() {
        if (currentShowId == null || currentZoneId == null) return;

        List<String> seatIds = new ArrayList<>(selectedSeatIds);
        reserveBtn.setEnabled(false);

        Result<ActiveOrderDTO> result;
        if (existingOrder != null) {
            result = purchasePresenter.addTicketsToOrder(
                    existingOrder.id, currentZoneId, seatIds, standingQty);
        } else {
            result = purchasePresenter.reserveTickets(
                    currentShowId, currentZoneId, seatIds, standingQty,
                    isRaffleEvent ? winnerRaffleCode : null);
        }

        if (result instanceof Result.Ok<ActiveOrderDTO> ok) {
            UI.getCurrent().navigate("purchase/" + ok.value().id);
        } else if (result instanceof Result.Fail<ActiveOrderDTO> fail) {
            reserveBtn.setEnabled(true);
            Notification n = Notification.show(fail.message());
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void updateReserveBar() {
        int count = selectedSeatIds.isEmpty() ? standingQty : selectedSeatIds.size();
        if (count == 0) {
            reserveBar.getStyle().set("display", "none");
            return;
        }
        double total = count * zonePricePerTicket;
        reserveBarLabel.setText(count + " ticket(s) selected · $" + String.format("%.2f", total));
        reserveBtn.setText(existingOrder != null ? "Add to Order" : "Reserve Tickets");
        reserveBar.getStyle().set("display", "block");
    }

    private void clearStateAndDots() {
        for (Div dot : selectedDotElements.values()) {
            dot.getStyle()
                    .set("background", "var(--lumo-success-color-50pct)")
                    .set("border", "1px solid var(--lumo-success-color)");
        }
        selectedDotElements.clear();
        selectedSeatIds.clear();
        standingQty = 0;
        currentZoneId = null;
        zonePricePerTicket = 0;
        reserveBar.getStyle().set("display", "none");
    }

    private void clearSelection() {
        clearStateAndDots();
        clearingSelection = true;
        standingQtyFields.forEach(f -> f.setValue(0));
        clearingSelection = false;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        event.getRouteParameters().get("eventId").ifPresent(this::loadPage);
    }

    private void loadPage(String eventId) {
        Result<EventDTO> eventResult = presenter.getEvent(eventId);
        if (eventResult instanceof Result.Ok<EventDTO> ok) {
            this.isRaffleEvent = ok.value().requiresRaffle;
            this.publicRaffleStatus = ok.value().raffleStatus;
            renderEventDetails(ok.value());
        } else if (eventResult instanceof Result.Fail<EventDTO> fail) {
            Notification n = Notification.show(fail.message());
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        // Edit-mode: if the logged-in user already has an active order for this event,
        // skip the shows list / raffle panel and let them edit that order directly.
        // The raffle/queue gates were cleared during the original reservation, so
        // addTicketsToOrder doesn't need to re-check them.
        ActiveOrderDTO maybeOrder = purchasePresenter.getCurrentActiveOrder();
        if (maybeOrder != null && eventId.equals(maybeOrder.eventId)) {
            this.existingOrder = maybeOrder;
            renderEditOrderPanel();
            currentShowId = existingOrder.showId;
            clearSelection();
            renderSeatMap(existingOrder.showId);
            return;
        }
        this.existingOrder = null;

        // For raffle events, winning (not the queue) gates seat selection: show the raffle
        // panel, and only reveal the shows/seat map to an eligible winner.
        if (isRaffleEvent) {
            renderRafflePanel(eventId);
        } else {
            loadShows(eventId);
        }
    }

    private void renderEditOrderPanel() {
        showsSection.removeAll();

        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("box-shadow", "var(--lumo-box-shadow-s)")
                .set("padding", "var(--lumo-space-l)");

        H3 title = new H3("Your current tickets");
        title.getStyle().set("margin-top", "0").set("margin-bottom", "var(--lumo-space-s)");
        card.add(title);

        Paragraph hint = new Paragraph(
                "Remove tickets from your order here, or add more from the seat map below. "
                + "When you're done, continue to checkout.");
        hint.getStyle()
                .set("margin", "0 0 var(--lumo-space-m)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        card.add(hint);

        if (existingOrder.ticketIds == null || existingOrder.ticketIds.isEmpty()) {
            Paragraph empty = new Paragraph(
                    "Your order is empty. Pick seats below to add tickets.");
            empty.getStyle().set("color", "var(--lumo-secondary-text-color)");
            card.add(empty);
        } else {
            for (String ticketId : existingOrder.ticketIds) {
                HorizontalLayout row = new HorizontalLayout();
                row.setWidthFull();
                row.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
                row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
                row.getStyle()
                        .set("padding", "var(--lumo-space-s) 0")
                        .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

                Span label = new Span("Ticket #" + ticketId);

                Button removeBtn = new Button("Remove", e -> {
                    Result<Void> r = purchasePresenter.removeTicket(existingOrder.id, ticketId);
                    if (r instanceof Result.Fail<Void> fail) {
                        Notification n = Notification.show(fail.message());
                        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
                    } else {
                        Notification n = Notification.show("Ticket removed.");
                        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                        loadPage(existingOrder.eventId);
                    }
                });
                removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

                row.add(label, removeBtn);
                card.add(row);
            }
        }

        Span subtotal = new Span("Subtotal: $" + String.format("%.2f", existingOrder.subtotal));
        subtotal.getStyle()
                .set("display", "block")
                .set("margin-top", "var(--lumo-space-m)")
                .set("font-weight", "600");
        card.add(subtotal);

        Button continueBtn = new Button("Continue to Checkout →",
                e -> UI.getCurrent().navigate("purchase/" + existingOrder.id));
        continueBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        continueBtn.getStyle().set("margin-top", "var(--lumo-space-m)");
        card.add(continueBtn);

        showsSection.add(card);
    }

    private void loadShows(String eventId) {
        Result<List<ShowDTO>> showsResult = presenter.getShowsForEvent(eventId);
        if (showsResult instanceof Result.Ok<List<ShowDTO>> ok) {
            renderShowsList(ok.value());
        } else if (showsResult instanceof Result.Fail<List<ShowDTO>> fail) {
            Notification n = Notification.show(fail.message());
            n.addThemeVariants(NotificationVariant.LUMO_WARNING);
        }
    }

    /** Raffle entry UI: guest prompt, register-before-deadline, or winner code + seat selection. */
    private void renderRafflePanel(String eventId) {
        showsSection.removeAll();
        showMapSection.removeAll();
        winnerRaffleCode = null;

        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("box-shadow", "var(--lumo-box-shadow-s)")
                .set("padding", "var(--lumo-space-l)");
        card.add(new H3("🎟 Raffle access"));

        if (!currentUser.isLoggedIn()) {
            if ("DRAWN".equals(publicRaffleStatus)) {
                // Registration is closed once the raffle has been drawn — don't invite a guest to register.
                card.add(new Paragraph("This raffle has already been drawn; registration is closed."));
            } else {
                card.add(new Paragraph("Tickets for this event are allocated by raffle. Log in to register."));
                Button login = new Button("Log in", e -> UI.getCurrent().navigate("login"));
                login.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                card.add(login);
            }
            showsSection.add(card);
            return;
        }

        Result<RaffleStatusDTO> statusResult = presenter.getRaffleStatus(eventId);
        if (statusResult instanceof Result.Fail<RaffleStatusDTO> fail) {
            card.add(new Paragraph(fail.message()));
            showsSection.add(card);
            return;
        }
        RaffleStatusDTO status = ((Result.Ok<RaffleStatusDTO>) statusResult).value();

        boolean eligibleWinner = false;
        if ("OPEN".equals(status.status)) {
            renderRaffleOpen(card, eventId, status);
        } else if ("DRAWN".equals(status.status)) {
            eligibleWinner = renderRaffleDrawn(card, status);
        } else {
            card.add(new Paragraph("This raffle is not currently open."));
        }

        showsSection.add(card); // raffle panel on top
        if (eligibleWinner) {
            // reveal the shows below the panel only for an eligible winner
            Result<List<ShowDTO>> showsResult = presenter.getShowsForEvent(eventId);
            if (showsResult instanceof Result.Ok<List<ShowDTO>> ok) {
                showsSection.add(buildShowsCard(ok.value()));
            } else if (showsResult instanceof Result.Fail<List<ShowDTO>> fail) {
                Notification n = Notification.show(fail.message());
                n.addThemeVariants(NotificationVariant.LUMO_WARNING);
            }
        }
    }

    private void renderRaffleOpen(Div card, String eventId, RaffleStatusDTO status) {
        if (status.registrationDeadline != null) {
            Paragraph deadline = new Paragraph("Registration closes: "
                    + status.registrationDeadline.format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")));
            deadline.getStyle().set("color", "var(--lumo-secondary-text-color)");
            card.add(deadline);
        }
        if (status.registered) {
            Paragraph done = new Paragraph("✅ You're registered. Winners are drawn once registration closes.");
            done.getStyle().set("color", "var(--lumo-success-text-color)");
            card.add(done);
        } else {
            Button register = new Button("Register for raffle", e -> {
                Result<Void> r = presenter.registerForRaffle(eventId);
                if (r instanceof Result.Ok) {
                    Notification n = Notification.show("Registered for the raffle.");
                    n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    renderRafflePanel(eventId);
                } else {
                    Notification n = Notification.show(((Result.Fail<Void>) r).message());
                    n.addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            });
            register.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            card.add(register);
        }
    }

    /** Renders the drawn-state message into {@code card}; returns true if the viewer is an eligible winner. */
    private boolean renderRaffleDrawn(Div card, RaffleStatusDTO status) {
        boolean codeValid = status.won && status.code != null
                && status.codeExpiresAt != null && status.codeExpiresAt.isAfter(java.time.LocalDateTime.now());
        if (codeValid) {
            winnerRaffleCode = status.code;
            Paragraph won = new Paragraph("🎉 You won! Your access code is " + status.code
                    + " (valid until " + status.codeExpiresAt.format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")) + ").");
            won.getStyle().set("color", "var(--lumo-success-text-color)");
            card.add(won, new Paragraph("Select a show below to choose your tickets."));
            return true;
        } else if (status.won) {
            card.add(new Paragraph("🎉 You won, but your access window has expired."));
        } else if (status.registered) {
            card.add(new Paragraph("The raffle has been drawn — you weren't selected this time."));
        } else {
            card.add(new Paragraph("Registration for this raffle has closed; you didn't enter."));
        }
        return false;
    }

    private void renderEventDetails(EventDTO event) {
        eventDetailsSection.removeAll();

        VerticalLayout hero = new VerticalLayout();
        hero.setWidthFull();
        hero.setAlignItems(FlexComponent.Alignment.START);
        hero.getStyle()
                .set("background", "linear-gradient(135deg, var(--lumo-primary-color) 0%, var(--lumo-primary-color-50pct) 100%)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("padding", "var(--lumo-space-xl)");

        H2 title = new H2(event.getName());
        title.getStyle().set("color", "white").set("margin", "0");

        Span typeSpan = new Span(event.getEventTypeName() != null ? event.getEventTypeName() : "Uncategorized");
        typeSpan.getStyle()
                .set("background", "rgba(255,255,255,0.25)")
                .set("color", "white")
                .set("padding", "2px var(--lumo-space-s)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("font-size", "var(--lumo-font-size-s)");

        hero.add(title, typeSpan);
        if (event.requiresRaffle) {
            hero.add(EventBrowseView.raffleBadge());
        }
        eventDetailsSection.add(hero);

        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("box-shadow", "var(--lumo-box-shadow-s)")
                .set("padding", "var(--lumo-space-l)");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

        FlexLayout detailsGrid = new FlexLayout();
        detailsGrid.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        detailsGrid.getStyle().set("gap", "var(--lumo-space-xl)");

        if (event.productionCompanyName != null)
            detailsGrid.add(detailItem("Production Company", event.productionCompanyName));
        if (event.artist != null && !event.artist.isBlank())
            detailsGrid.add(detailItem("Artist", event.artist));
        if (event.location != null && !event.location.isBlank())
            detailsGrid.add(detailItem("Location", event.location));
        if (event.startDate != null)
            detailsGrid.add(detailItem("Start", event.startDate.format(formatter)));
        if (event.endDate != null)
            detailsGrid.add(detailItem("End", event.endDate.format(formatter)));
        if (event.minPrice > 0 || event.maxPrice > 0)
            detailsGrid.add(detailItem("Price Range", String.format("$%.2f – $%.2f", event.minPrice, event.maxPrice)));
        if (event.ticketsSold > 0)
            detailsGrid.add(detailItem("Tickets Sold", String.valueOf(event.ticketsSold)));

        card.add(detailsGrid);
        eventDetailsSection.add(card);
    }

    private Div detailItem(String label, String value) {
        Div item = new Div();
        item.getStyle().set("min-width", "140px");
        Span lbl = new Span(label);
        lbl.getStyle()
                .set("display", "block")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.05em")
                .set("margin-bottom", "2px");
        Span val = new Span(value);
        val.getStyle().set("display", "block").set("font-weight", "600");
        item.add(lbl, val);
        return item;
    }

    private void renderShowsList(List<ShowDTO> shows) {
        showsSection.removeAll();
        showsSection.add(buildShowsCard(shows));
    }

    private Div buildShowsCard(List<ShowDTO> shows) {
        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("box-shadow", "var(--lumo-box-shadow-s)")
                .set("padding", "var(--lumo-space-l)");

        H3 header = new H3("Shows");
        header.getStyle().set("margin-top", "0").set("margin-bottom", "var(--lumo-space-m)");
        card.add(header);

        if (shows == null || shows.isEmpty()) {
            Paragraph none = new Paragraph("No shows scheduled for this event.");
            none.getStyle().set("color", "var(--lumo-secondary-text-color)");
            card.add(none);
            return card;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE, MMM dd yyyy  HH:mm");
        for (ShowDTO show : shows) {
            HorizontalLayout row = new HorizontalLayout();
            row.setWidthFull();
            row.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
            row.getStyle()
                    .set("padding", "var(--lumo-space-s) 0")
                    .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

            Span dateSpan = new Span(show.date != null ? show.date.format(fmt) : "N/A");
            dateSpan.getStyle().set("flex", "1");

            Span capacitySpan = new Span("Capacity: " + show.totalCapacity);
            capacitySpan.getStyle()
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("margin-right", "var(--lumo-space-m)");

            Button viewMapBtn = new Button("View Map");
            viewMapBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
            viewMapBtn.addClickListener(e -> loadShowMap(show.showId));

            row.add(dateSpan, capacitySpan, viewMapBtn);
            card.add(row);
        }

        return card;
    }

    private void loadShowMap(String showId) {
        currentShowId = showId;
        clearSelection();
        stopQueuePolling();

        // Raffle events don't use the virtual queue — winning a valid code is the gate.
        if (isRaffleEvent) {
            renderSeatMap(showId);
            return;
        }

        // Release any prior queue slot from a different show before joining a new one
        if (joinedShowId != null && !joinedShowId.equals(showId)) {
            queuePresenter.release(joinedShowId);
            joinedShowId = null;
        }

        Result<QueueStatusDTO> joinResult = queuePresenter.join(showId);
        if (joinResult instanceof Result.Fail<QueueStatusDTO> fail) {
            Notification n = Notification.show(fail.message());
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        joinedShowId = showId;

        QueueStatusDTO status = ((Result.Ok<QueueStatusDTO>) joinResult).value();
        if (status.isAllowed()) {
            renderSeatMap(showId);
        } else {
            renderWaitingCard(showId, status.getPosition());
            startQueuePolling(showId);
        }
    }

    private void renderSeatMap(String showId) {
        Result<VenueMapDTO> result = presenter.getShowMap(showId);
        if (result instanceof Result.Ok<VenueMapDTO> ok) {
            renderShowMap(ok.value());
        } else if (result instanceof Result.Fail<VenueMapDTO> fail) {
            Notification n = Notification.show(fail.message());
            n.addThemeVariants(NotificationVariant.LUMO_WARNING);
        }
    }

    private void renderWaitingCard(String showId, int position) {
        showMapSection.removeAll();
        waitingOnShowId = showId;

        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("box-shadow", "var(--lumo-box-shadow-s)")
                .set("padding", "var(--lumo-space-l)")
                .set("text-align", "center");

        H3 heading = new H3("You're in the virtual queue");
        heading.getStyle().set("margin-top", "0");

        waitingPositionLabel = new Span("Position: #" + position);
        waitingPositionLabel.getStyle()
                .set("display", "block")
                .set("font-size", "var(--lumo-font-size-l)")
                .set("margin", "var(--lumo-space-m) 0");

        Paragraph hint = new Paragraph(
                "When a spot opens up, the seat map will appear here automatically.");
        hint.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Button leaveBtn = new Button("Leave queue", e -> {
            stopQueuePolling();
            queuePresenter.release(showId);
            waitingOnShowId = null;
            joinedShowId = null;
            showMapSection.removeAll();
        });
        leaveBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        card.add(heading, waitingPositionLabel, hint, leaveBtn);
        showMapSection.add(card);
        showMapSection.getElement().scrollIntoView();
    }

    private void startQueuePolling(String showId) {
        UI ui = UI.getCurrent();
        ui.setPollInterval(2000);
        queuePollListener = ui.addPollListener(event -> {
            Result<QueueStatusDTO> r = queuePresenter.checkStatus(showId);
            if (r instanceof Result.Ok<QueueStatusDTO> ok) {
                QueueStatusDTO status = ok.value();
                if (status.isAllowed()) {
                    stopQueuePolling();
                    waitingOnShowId = null;
                    renderSeatMap(showId);
                } else if (waitingPositionLabel != null) {
                    waitingPositionLabel.setText("Position: #" + status.getPosition());
                }
            }
        });
    }

    private void stopQueuePolling() {
        if (queuePollListener != null) {
            queuePollListener.remove();
            queuePollListener = null;
        }
        UI ui = UI.getCurrent();
        if (ui != null) ui.setPollInterval(-1);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        stopQueuePolling();
        if (joinedShowId != null) {
            // No-op if the slot was already consumed by a successful reservation
            // (completeSession ran in ActiveOrderService), but safe and idempotent.
            queuePresenter.release(joinedShowId);
            joinedShowId = null;
        }
        waitingOnShowId = null;
        super.onDetach(detachEvent);
    }

    private void renderShowMap(VenueMapDTO venueMap) {
        showMapSection.removeAll();
        standingQtyFields.clear();

        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("box-shadow", "var(--lumo-box-shadow-s)")
                .set("padding", "var(--lumo-space-l)");

        H3 header = new H3("Show Map" + (venueMap.mapName != null && !venueMap.mapName.isBlank()
                ? ": " + venueMap.mapName : ""));
        header.getStyle().set("margin-top", "0").set("margin-bottom", "var(--lumo-space-m)");

        Paragraph hint = new Paragraph("Click available seats to select them, then click Reserve Tickets.");
        hint.getStyle()
                .set("margin", "0 0 var(--lumo-space-m)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");

        card.add(header, hint);

        if (venueMap.zones == null || venueMap.zones.isEmpty()) {
            card.add(new Paragraph("No zones defined for this show."));
            showMapSection.add(card);
            return;
        }

        FlexLayout zonesLayout = new FlexLayout();
        zonesLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        zonesLayout.getStyle().set("gap", "var(--lumo-space-l)");

        for (ZoneDTO zone : venueMap.zones) {
            zonesLayout.add(buildZoneCard(zone));
        }

        card.add(zonesLayout);
        showMapSection.add(card);
        showMapSection.getElement().scrollIntoView();
    }

    private Div buildZoneCard(ZoneDTO zone) {
        Div zoneCard = new Div();
        zoneCard.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "var(--lumo-space-m)")
                .set("min-width", "200px");

        H4 zoneName = new H4(zone.zoneName);
        zoneName.getStyle().set("margin-top", "0").set("margin-bottom", "var(--lumo-space-xs)");

        Span meta = new Span((zone.standing ? "Standing" : "Seated") + "  ·  $" + String.format("%.2f", zone.basePrice));
        meta.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("display", "block")
                .set("margin-bottom", "var(--lumo-space-s)");

        zoneCard.add(zoneName, meta);

        if (zone.standing) {
            Span avail = new Span(zone.standingAvailable + " / " + zone.standingCapacity + " spots available");
            avail.getStyle()
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("color", zone.standingAvailable > 0
                            ? "var(--lumo-success-color)" : "var(--lumo-error-color)");
            zoneCard.add(avail);

            if (zone.standingAvailable > 0) {
                IntegerField qtyField = new IntegerField("Quantity");
                qtyField.setMin(0);
                qtyField.setMax(zone.standingAvailable);
                qtyField.setStepButtonsVisible(true);
                qtyField.setValue(0);
                qtyField.getStyle().set("margin-top", "var(--lumo-space-s)");
                standingQtyFields.add(qtyField);
                qtyField.addValueChangeListener(e -> {
                    if (clearingSelection || e.getValue() == null) return;
                    int qty = e.getValue();
                    if (qty <= 0) {
                        if (zone.zoneId.equals(currentZoneId)) {
                            clearStateAndDots();
                            clearingSelection = true;
                            standingQtyFields.stream()
                                    .filter(f -> f != qtyField)
                                    .forEach(f -> f.setValue(0));
                            clearingSelection = false;
                        }
                        return;
                    }
                    if (currentZoneId != null && !currentZoneId.equals(zone.zoneId)) {
                        clearStateAndDots();
                        clearingSelection = true;
                        standingQtyFields.stream()
                                .filter(f -> f != qtyField)
                                .forEach(f -> f.setValue(0));
                        clearingSelection = false;
                    }
                    currentZoneId = zone.zoneId;
                    zonePricePerTicket = zone.basePrice;
                    standingQty = qty;
                    updateReserveBar();
                });
                zoneCard.add(qtyField);
            }
        } else if (zone.seats != null && !zone.seats.isEmpty()) {
            zoneCard.add(buildSeatGrid(zone));
            long takenCount = zone.seats.stream().filter(s -> s.isTaken).count();
            long availCount = zone.seats.size() - takenCount;
            Span avail = new Span(availCount + " / " + zone.seats.size() + " seats available");
            avail.getStyle()
                    .set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("display", "block")
                    .set("margin-top", "var(--lumo-space-xs)");
            zoneCard.add(avail);
        } else {
            Span dims = new Span(zone.rows + " rows × " + zone.seatsPerRow + " seats");
            dims.getStyle()
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("color", "var(--lumo-secondary-text-color)");
            zoneCard.add(dims);
        }

        return zoneCard;
    }

    private VerticalLayout buildSeatGrid(ZoneDTO zone) {
        VerticalLayout grid = new VerticalLayout();
        grid.setPadding(false);
        grid.setSpacing(false);
        grid.getStyle().set("gap", "3px");

        int maxRow = zone.seats.stream().mapToInt(s -> s.rowNumber).max().orElse(0);
        for (int r = 1; r <= maxRow; r++) {
            final int rowNum = r;
            HorizontalLayout rowLayout = new HorizontalLayout();
            rowLayout.setPadding(false);
            rowLayout.setSpacing(false);
            rowLayout.getStyle().set("gap", "3px");

            Span rowLabel = new Span(String.valueOf((char) ('A' + r - 1)));
            rowLabel.getStyle()
                    .set("width", "18px")
                    .set("min-width", "18px")
                    .set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("line-height", "18px")
                    .set("text-align", "center");
            rowLayout.add(rowLabel);

            zone.seats.stream()
                    .filter(s -> s.rowNumber == rowNum)
                    .sorted((a, b) -> Integer.compare(a.seatNumber, b.seatNumber))
                    .forEach(seat -> rowLayout.add(buildSeatDot(seat, zone)));

            grid.add(rowLayout);
        }
        return grid;
    }

    private Div buildSeatDot(SeatDTO seat, ZoneDTO zone) {
        Div dot = new Div();
        dot.getStyle()
                .set("width", "18px")
                .set("height", "18px")
                .set("min-width", "18px")
                .set("border-radius", "3px")
                .set("display", "inline-block");

        if (seat.isTaken) {
            dot.getStyle()
                    .set("background", "var(--lumo-error-color-50pct)")
                    .set("border", "1px solid var(--lumo-error-color)");
            dot.setTitle("Row " + seat.rowNumber + " Seat " + seat.seatNumber + " (taken)");
        } else {
            dot.getStyle()
                    .set("background", "var(--lumo-success-color-50pct)")
                    .set("border", "1px solid var(--lumo-success-color)")
                    .set("cursor", "pointer");
            dot.setTitle("Row " + seat.rowNumber + " Seat " + seat.seatNumber + " (available)");

            dot.addClickListener(e -> {
                if (selectedSeatIds.contains(seat.seatId)) {
                    // Deselect — no zone logic needed, just remove
                    selectedSeatIds.remove(seat.seatId);
                    selectedDotElements.remove(seat.seatId);
                    dot.getStyle()
                            .set("background", "var(--lumo-success-color-50pct)")
                            .set("border", "1px solid var(--lumo-success-color)");
                    if (selectedSeatIds.isEmpty()) {
                        currentZoneId = null;
                        zonePricePerTicket = 0;
                    }
                } else {
                    // Select — clear if switching zones
                    if (currentZoneId != null && !currentZoneId.equals(zone.zoneId)) {
                        clearSelection();
                    }
                    currentZoneId = zone.zoneId;
                    zonePricePerTicket = zone.basePrice;
                    selectedSeatIds.add(seat.seatId);
                    selectedDotElements.put(seat.seatId, dot);
                    dot.getStyle()
                            .set("background", "var(--lumo-primary-color-50pct)")
                            .set("border", "1px solid var(--lumo-primary-color)");
                }
                updateReserveBar();
            });
        }
        return dot;
    }
}
