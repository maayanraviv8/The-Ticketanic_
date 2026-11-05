package presentation.view;

import application.dto.EventDTO;
import application.dto.ShowDTO;
import application.dto.VenueMapDTO;
import application.dto.ZoneDTO;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.details.DetailsVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import domain.Aggregates.productioncompany.Permissions;
import presentation.CurrentUser;
import presentation.presenter.EventManagementPresenter;
import presentation.Result;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Route(value = "company/:companyId/layout", layout = MainLayout.class)
@PageTitle("Hall Layout Management")
public class HallLayoutView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

    private final EventManagementPresenter presenter;
    private final CurrentUser currentUser;
    private String companyId;

    public HallLayoutView(EventManagementPresenter presenter, CurrentUser currentUser) {
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
            event.forwardTo("events");
            return;
        }
        if (!currentUser.isLoggedIn() ||
                !currentUser.hasCompanyPermission(companyId, Permissions.EVENT_EDITING)) {
            event.forwardTo("access-denied");
            return;
        }
        loadPage();
    }

    private void loadPage() {
        removeAll();

        H2 title = new H2("Hall Layout Management");
        title.getStyle()
                .set("background", "linear-gradient(135deg, var(--lumo-primary-color), #141d2e)")
                .set("padding", "2rem")
                .set("color", "white")
                .set("margin", "0")
                .set("width", "100%");
        add(title);

        VerticalLayout content = new VerticalLayout();
        content.getStyle().set("padding", "1rem 2rem");

        Result<List<EventDTO>> eventsResult = presenter.getCompanyEvents(companyId);
        if (eventsResult instanceof Result.Fail<List<EventDTO>> fail) {
            Notification.show(fail.message());
            return;
        }

        List<EventDTO> events = ((Result.Ok<List<EventDTO>>) eventsResult).value();
        if (events.isEmpty()) {
            content.add(new Paragraph("No events found for this company."));
        } else {
            events.forEach(ev -> content.add(buildEventSection(ev)));
        }
        add(content);

        Button back = new Button("Back", e -> UI.getCurrent().navigate("company/" + companyId));
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        add(back);
    }

    private Details buildEventSection(EventDTO event) {
        VerticalLayout showsLayout = new VerticalLayout();
        showsLayout.setPadding(false);

        Result<List<ShowDTO>> showsResult = presenter.getShowsByEvent(event.eventId);
        if (showsResult instanceof Result.Fail<List<ShowDTO>> fail) {
            showsLayout.add(new Paragraph(fail.message()));
        } else {
            List<ShowDTO> shows = ((Result.Ok<List<ShowDTO>>) showsResult).value();
            if (shows.isEmpty()) {
                showsLayout.add(new Paragraph("No shows defined for this event."));
            } else {
                shows.forEach(show -> showsLayout.add(buildShowCard(show)));
            }
        }

        Details section = new Details(event.name, showsLayout);
        section.addThemeVariants(DetailsVariant.FILLED);
        return section;
    }

    private HorizontalLayout buildShowCard(ShowDTO show) {
        HorizontalLayout card = new HorizontalLayout();
        card.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "1rem")
                .set("align-items", "center")
                .set("width", "100%");

        Paragraph date = new Paragraph(show.date != null ? show.date.format(DATE_FMT) : "No date");
        date.getStyle().set("flex", "1").set("margin", "0");

        Result<VenueMapDTO> layoutResult = presenter.getHallLayout(String.valueOf(show.showId));
        boolean hasLayout = layoutResult instanceof Result.Ok;

        HorizontalLayout actions = new HorizontalLayout();
        if (hasLayout) {
            VenueMapDTO layout = ((Result.Ok<VenueMapDTO>) layoutResult).value();
            Button editBtn = new Button("Edit Layout",
                    e -> openLayoutDialog(show, layout));
            editBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);

            Button removeBtn = new Button("Remove Layout",
                    e -> confirmRemoveLayout(show));
            removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

            actions.add(editBtn, removeBtn);
        } else {
            Button defineBtn = new Button("Define Layout",
                    e -> openLayoutDialog(show, null));
            defineBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            actions.add(defineBtn);
        }

        card.add(date, actions);
        return card;
    }

    private void openLayoutDialog(ShowDTO show, VenueMapDTO existing) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(existing == null ? "Define Hall Layout" : "Edit Hall Layout");

        TextField mapName = new TextField("Map Name");
        if (existing != null && existing.mapName != null)
            mapName.setValue(existing.mapName);

        VerticalLayout zonesLayout = new VerticalLayout();
        zonesLayout.setPadding(false);
        List<ZoneDTO> zones = new ArrayList<>();

        if (existing != null && existing.zones != null) {
            existing.zones.forEach(z -> {
                zones.add(z);
                zonesLayout.add(buildZoneRow(z, zones, zonesLayout));
            });
        }

        Button addZoneBtn = new Button("+ Add Zone", e -> {
            ZoneDTO newZone = new ZoneDTO();
            zones.add(newZone);
            zonesLayout.add(buildZoneRow(newZone, zones, zonesLayout));
        });
        addZoneBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);

        VerticalLayout form = new VerticalLayout(mapName, new H3("Zones"), zonesLayout, addZoneBtn);
        form.setPadding(false);
        dialog.add(form);

        Button saveBtn = new Button("Save", e -> {
            VenueMapDTO dto = new VenueMapDTO();
            dto.mapName = mapName.getValue();
            dto.showId = String.valueOf(show.showId);
            dto.zones = zones;
            dto.totalCapacity = zones.stream().mapToInt(z -> z.totalCapacity).sum();

            Result<VenueMapDTO> result = existing == null
                    ? presenter.defineHallLayout(String.valueOf(show.showId), dto)
                    : presenter.updateHallLayout(String.valueOf(show.showId), dto);

            if (result instanceof Result.Fail<?> fail) {
                Notification.show(fail.message());
            } else {
                dialog.close();
                loadPage();
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    private HorizontalLayout buildZoneRow(ZoneDTO zone, List<ZoneDTO> zones, VerticalLayout zonesLayout) {
        HorizontalLayout row = new HorizontalLayout();
        row.setAlignItems(Alignment.BASELINE);

        TextField nameField = new TextField("Zone Name");
        nameField.setValue(zone.zoneName != null ? zone.zoneName : "");
        nameField.addValueChangeListener(e -> zone.zoneName = e.getValue());

        Checkbox standingCheck = new Checkbox("Standing");
        standingCheck.setValue(zone.standing);

        NumberField priceField = new NumberField("Base Price");
        priceField.setValue(zone.basePrice);
        priceField.addValueChangeListener(e -> zone.basePrice = e.getValue() != null ? e.getValue() : 0);

        IntegerField capacityField = new IntegerField("Capacity");
        IntegerField rowsField = new IntegerField("Rows");
        IntegerField seatsField = new IntegerField("Seats/Row");

        capacityField.setValue(zone.standingCapacity);
        rowsField.setValue(zone.rows);
        seatsField.setValue(zone.seatsPerRow);

        capacityField.setVisible(zone.standing);
        rowsField.setVisible(!zone.standing);
        seatsField.setVisible(!zone.standing);

        standingCheck.addValueChangeListener(e -> {
            zone.standing = e.getValue();
            capacityField.setVisible(e.getValue());
            rowsField.setVisible(!e.getValue());
            seatsField.setVisible(!e.getValue());
        });

        capacityField.addValueChangeListener(e -> {
            zone.standingCapacity = e.getValue() != null ? e.getValue() : 0;
            zone.totalCapacity = zone.standingCapacity;
        });
        rowsField.addValueChangeListener(e -> {
            zone.rows = e.getValue() != null ? e.getValue() : 0;
            zone.totalCapacity = zone.rows * zone.seatsPerRow;
        });
        seatsField.addValueChangeListener(e -> {
            zone.seatsPerRow = e.getValue() != null ? e.getValue() : 0;
            zone.totalCapacity = zone.rows * zone.seatsPerRow;
        });

        Button removeBtn = new Button("✕", e -> {
            zones.remove(zone);
            zonesLayout.remove(row);
        });
        removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

        row.add(nameField, standingCheck, priceField, rowsField, seatsField, capacityField, removeBtn);
        return row;
    }

    private void confirmRemoveLayout(ShowDTO show) {
        Dialog confirm = new Dialog();
        confirm.setHeaderTitle("Remove Hall Layout");
        confirm.add(new Paragraph("Are you sure you want to remove the hall layout for this show?"));

        Button removeBtn = new Button("Remove", e -> {
            Result<Void> result = presenter.removeHallLayout(String.valueOf(show.showId));
            if (result instanceof Result.Fail<Void> fail) {
                Notification.show(fail.message());
            } else {
                confirm.close();
                loadPage();
            }
        });
        removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);

        Button cancelBtn = new Button("Cancel", e -> confirm.close());
        confirm.getFooter().add(cancelBtn, removeBtn);
        confirm.open();
    }
}