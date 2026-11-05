package presentation.view;

import application.dto.ShowDTO;
import application.dto.VenueMapDTO;
import application.dto.ZoneDTO;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import domain.Aggregates.productioncompany.Permissions;
import presentation.CurrentUser;
import presentation.Result;
import presentation.presenter.ShowPresenter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Route(value = "company/:companyId/events/:eventId/shows", layout = MainLayout.class)
@PageTitle("Show Management")
public class ShowManagementView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

    private final ShowPresenter presenter;
    private final CurrentUser currentUser;
    private String companyId;
    private String eventId;

    public ShowManagementView(ShowPresenter presenter, CurrentUser currentUser) {
        this.presenter = presenter;
        this.currentUser = currentUser;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = event.getRouteParameters().get("companyId").orElse(null);
        eventId   = event.getRouteParameters().get("eventId").orElse(null);
        if (companyId == null || eventId == null) {
            event.forwardTo("events");
            return;
        }
        if (!currentUser.isLoggedIn() ||
                (!currentUser.hasCompanyPermission(companyId, Permissions.EVENT_EDITING) &&
                 !currentUser.hasCompanyPermission(companyId, Permissions.EDIT_VENUE_LAYOUT))) {
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
        H2 title = new H2("Show Management");
        title.getStyle().set("margin", "0").set("color", "white").set("flex", "1");

        header.add(title);
        if (currentUser.hasCompanyPermission(companyId, Permissions.EVENT_EDITING)) {
            Button createBtn = new Button("+ Create Show", e -> openCreateShowDialog());
            createBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            header.add(createBtn);
        }
        add(header);

        VerticalLayout content = new VerticalLayout();
        content.getStyle().set("padding", "1rem 2rem");

        Result<List<ShowDTO>> result = presenter.getShowsByEvent(eventId);
        if (result instanceof Result.Fail<List<ShowDTO>> fail) {
            content.add(new Paragraph("Failed to load shows: " + fail.message()));
        } else {
            List<ShowDTO> shows = ((Result.Ok<List<ShowDTO>>) result).value();
            if (shows.isEmpty()) {
                content.add(new Paragraph("No shows yet. Click '+ Create Show' to add one."));
            } else {
                FlexLayout cards = new FlexLayout();
                cards.getStyle().set("flex-wrap", "wrap").set("gap", "1rem");
                shows.forEach(show -> cards.add(buildShowCard(show)));
                content.add(cards);
            }
        }
        add(content);

        Button back = new Button("Back", e -> UI.getCurrent().navigate(
                "company/" + companyId + "/events"));
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        add(back);
    }

    private VerticalLayout buildShowCard(ShowDTO show) {
        VerticalLayout card = new VerticalLayout();
        card.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "1rem")
                .set("min-width", "280px")
                .set("max-width", "380px")
                .set("flex", "1");

        Paragraph date = new Paragraph(show.date != null ? show.date.format(DATE_FMT) : "No date");
        date.getStyle().set("margin", "0").set("font-weight", "bold");

        Paragraph capacity = new Paragraph("Capacity: " + show.totalCapacity);
        capacity.getStyle().set("color", "var(--lumo-secondary-text-color)").set("margin", "0.25rem 0 0 0");

        HorizontalLayout actions = new HorizontalLayout();

        Button editBtn = new Button("Edit", e -> openEditShowDialog(show));
        editBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);

        actions.add(editBtn);
        if (currentUser.hasCompanyPermission(companyId, Permissions.EVENT_EDITING)) {
            Button deleteBtn = new Button("Delete", e -> confirmDelete(show));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            actions.add(deleteBtn);
        }
        card.add(date, capacity, actions);
        return card;
    }

    // ── Create dialog ─────────────────────────────────────────────────────────

    private void openCreateShowDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Create Show");

        DateTimePicker datePicker = new DateTimePicker("Show Date");

        VerticalLayout form = new VerticalLayout(datePicker);
        form.setPadding(false);

        TextField mapNameField = new TextField("Map Name");
        VerticalLayout zonesLayout = new VerticalLayout();
        zonesLayout.setPadding(false);
        List<ZoneDTO> zones = new ArrayList<>();

        if (currentUser.hasCompanyPermission(companyId, Permissions.EVENT_EDITING)) {
            Button addZoneBtn = new Button("+ Add Zone", ev -> {
                ZoneDTO newZone = new ZoneDTO();
                zones.add(newZone);
                zonesLayout.add(buildZoneRow(newZone, zones, zonesLayout, null));
            });
            addZoneBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);

            VerticalLayout venueMapContent = new VerticalLayout(mapNameField, new H3("Zones"), zonesLayout, addZoneBtn);
            venueMapContent.setPadding(false);

            Details venueMapSection = new Details("Define Venue Map (optional)", venueMapContent);
            venueMapSection.setOpened(false);
            form.add(venueMapSection);
        }

        dialog.add(form);

        Button saveBtn = new Button("Save", e -> {
            if (datePicker.getValue() == null) {
                Notification.show("Show date is required.");
                return;
            }

            ShowDTO dto = new ShowDTO();
            dto.date = datePicker.getValue();
            dto.eventId = eventId;
            if (!zones.isEmpty()) dto.zones = zones;

            Result<ShowDTO> createResult = presenter.createShow(eventId, dto);
            if (createResult instanceof Result.Fail<?> fail) { Notification.show(fail.message()); return; }

            dialog.close();
            loadPage();
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    // ── Edit dialog (show metadata + venue map, merged) ───────────────────────

    private void openEditShowDialog(ShowDTO existing) {
        VenueMapDTO existingMap = null;
        Result<VenueMapDTO> mapLookup = presenter.getHallLayout(existing.showId);
        if (mapLookup instanceof Result.Ok<VenueMapDTO> ok) existingMap = ok.value();
        final VenueMapDTO finalExistingMap = existingMap;

        boolean canEditMap = currentUser.hasCompanyPermission(companyId, Permissions.EDIT_VENUE_LAYOUT)
                || currentUser.hasCompanyPermission(companyId, Permissions.EVENT_EDITING);

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Show");
        dialog.setWidth("700px");

        // ── Show metadata fields ──────────────────────────────────────────────
        boolean canEditDate = currentUser.hasCompanyPermission(companyId, Permissions.EVENT_EDITING);

        DateTimePicker datePicker = new DateTimePicker("Show Date");
        if (existing.date != null) datePicker.setValue(existing.date);
        datePicker.setReadOnly(!canEditDate);

        IntegerField capacityField = new IntegerField("Total Capacity");
        capacityField.setValue(existing.totalCapacity);
        capacityField.setMin(0);
        capacityField.setReadOnly(true);

        VerticalLayout form = new VerticalLayout(datePicker, capacityField);
        form.setPadding(false);

        Button saveBtn = new Button("Save");
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        // Shared zone list — populated (and optionally displayed) depending on permission
        List<ZoneDTO> zones = new ArrayList<>();

        if (canEditMap) {
            // ── Full
            // editor embedded in the dialog ──────────────────
            TextField mapNameField = new TextField("Map Name");
            if (finalExistingMap != null && finalExistingMap.mapName != null)
                mapNameField.setValue(finalExistingMap.mapName);

            VerticalLayout zonesLayout = new VerticalLayout();
            zonesLayout.setPadding(false);

            Runnable refreshStatus = () -> {
                int mapTotal = zones.stream().mapToInt(z -> z.totalCapacity).sum();
                capacityField.setValue(mapTotal);
            };

            if (finalExistingMap != null && finalExistingMap.zones != null) {
                finalExistingMap.zones.forEach(z -> {
                    zones.add(z);
                    zonesLayout.add(buildZoneRow(z, zones, zonesLayout, refreshStatus));
                });
            }

            Button addZoneBtn = new Button("+ Add Zone", e -> {
                ZoneDTO newZone = new ZoneDTO();
                zones.add(newZone);
                zonesLayout.add(buildZoneRow(newZone, zones, zonesLayout, refreshStatus));
                refreshStatus.run();
            });
            addZoneBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);

            H3 venueMapTitle = new H3("Venue Map");
            venueMapTitle.getStyle().set("margin", "1.5rem 0 0.5rem 0");

            form.add(venueMapTitle, mapNameField, new H3("Zones"), zonesLayout, addZoneBtn);

            if (finalExistingMap != null) {
                Button removeMapBtn = new Button("Remove Venue Map", ev -> {
                    Result<Void> r = presenter.removeHallLayout(existing.showId);
                    if (r instanceof Result.Fail<Void> fail) {
                        Notification.show(fail.message());
                    } else {
                        dialog.close();
                        loadPage();
                    }
                });
                removeMapBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
                removeMapBtn.getStyle().set("margin-top", "1rem");
                form.add(removeMapBtn);
            }

            refreshStatus.run();

            saveBtn.addClickListener(e -> {
                LocalDateTime date = datePicker.getValue();
                if (date == null) {
                    Notification.show("Show date is required.");
                    return;
                }
                if (date.isBefore(LocalDateTime.now())) {
                    Notification.show("Show date must be in the future.");
                    return;
                }

                // Persist the venue map first; the service derives the show's capacity from it.
                if (!zones.isEmpty()) {
                    VenueMapDTO mapDto = buildVenueMapDto(existing.showId, mapNameField.getValue(), zones);
                    Result<VenueMapDTO> mapSaveResult = finalExistingMap == null
                            ? presenter.defineHallLayout(existing.showId, mapDto)
                            : presenter.updateHallLayout(existing.showId, mapDto);
                    if (mapSaveResult instanceof Result.Fail<?> fail) {
                        Notification.show("Venue map not changed: " + fail.message());
                    }
                }

                // Capacity is owned by the venue map, so only the date is sent here (no zones).
                // A rejected map edit therefore never moves the show's capacity.
                ShowDTO dto = new ShowDTO();
                dto.date = date;
                dto.eventId = existing.eventId;

                Result<ShowDTO> showResult = presenter.editShow(existing.showId, dto);
                if (showResult instanceof Result.Fail<?> fail) { Notification.show(fail.message()); return; }

                dialog.close();
                loadPage();
            });

        } else {
            // No DEFINE_HALL_LAYOUT permission: show read-only map summary;
            // silently load existing zones so the service can preserve capacity.
            if (finalExistingMap != null) {
                if (finalExistingMap.zones != null) zones.addAll(finalExistingMap.zones);
                int mapTotal = zones.stream().mapToInt(z -> z.totalCapacity).sum();
                Paragraph mapInfo = new Paragraph("Venue map: "
                        + (finalExistingMap.mapName != null ? finalExistingMap.mapName : "Unnamed")
                        + " — " + mapTotal + " seat(s) across " + zones.size() + " zone(s)");
                mapInfo.getStyle().set("color", "var(--lumo-secondary-text-color)").set("margin-top", "0.5rem");
                form.add(mapInfo);
            }

            saveBtn.addClickListener(e -> {
                if (datePicker.getValue() == null) {
                    Notification.show("Show date is required.");
                    return;
                }

                ShowDTO dto = new ShowDTO();
                dto.date = datePicker.getValue();
                dto.eventId = existing.eventId;
                dto.totalCapacity = capacityField.getValue() != null ? capacityField.getValue() : 0;
                if (!zones.isEmpty()) dto.zones = zones;

                Result<ShowDTO> showResult = presenter.editShow(existing.showId, dto);
                if (showResult instanceof Result.Fail<?> fail) { Notification.show(fail.message()); return; }

                dialog.close();
                loadPage();
            });
        }

        dialog.add(form);

        Button cancelBtn = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private VenueMapDTO buildVenueMapDto(String showId, String mapName, List<ZoneDTO> zones) {
        VenueMapDTO dto = new VenueMapDTO();
        dto.showId = showId;
        dto.mapName = mapName;
        dto.zones = zones;
        dto.totalCapacity = zones.stream().mapToInt(z -> z.totalCapacity).sum();
        dto.zoneCount = zones.size();
        return dto;
    }

    private HorizontalLayout buildZoneRow(ZoneDTO zone, List<ZoneDTO> zones, VerticalLayout zonesLayout,
                                          Runnable onCapacityChange) {
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
        IntegerField rowsField    = new IntegerField("Rows");
        IntegerField seatsField   = new IntegerField("Seats/Row");

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
            if (onCapacityChange != null) onCapacityChange.run();
        });
        rowsField.addValueChangeListener(e -> {
            zone.rows = e.getValue() != null ? e.getValue() : 0;
            zone.totalCapacity = zone.rows * zone.seatsPerRow;
            if (onCapacityChange != null) onCapacityChange.run();
        });
        seatsField.addValueChangeListener(e -> {
            zone.seatsPerRow = e.getValue() != null ? e.getValue() : 0;
            zone.totalCapacity = zone.rows * zone.seatsPerRow;
            if (onCapacityChange != null) onCapacityChange.run();
        });

        Button removeBtn = new Button("✕", e -> {
            zones.remove(zone);
            zonesLayout.remove(row);
            if (onCapacityChange != null) onCapacityChange.run();
        });
        removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

        row.add(nameField, standingCheck, priceField, rowsField, seatsField, capacityField, removeBtn);
        return row;
    }

    private void confirmDelete(ShowDTO show) {
        Dialog confirm = new Dialog();
        confirm.setHeaderTitle("Delete Show");
        String dateStr = show.date != null ? show.date.format(DATE_FMT) : "this show";
        confirm.add(new Paragraph("Are you sure you want to delete the show on " + dateStr + "?"));

        Button deleteBtn = new Button("Delete", e -> {
            Result<Void> result = presenter.removeShow(show.showId);
            if (result instanceof Result.Fail<Void> fail) {
                Notification.show(fail.message());
            } else {
                confirm.close();
                loadPage();
            }
        });
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);

        Button cancelBtn = new Button("Cancel", e -> confirm.close());
        confirm.getFooter().add(cancelBtn, deleteBtn);
        confirm.open();
    }
}