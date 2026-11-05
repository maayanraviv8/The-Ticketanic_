package presentation.view;

import application.dto.DiscountPolicyDTO;
import application.dto.EventDTO;
import application.dto.PurchasePolicyDTO;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.*;
import domain.Aggregates.productioncompany.Permissions;
import presentation.CurrentUser;
import presentation.presenter.EventManagementPresenter;
import presentation.Result;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Route(value = "company/:companyId/events", layout = MainLayout.class)
@PageTitle("Event Management")
public class EventManagementView extends VerticalLayout implements BeforeEnterObserver {

    private static final String SALE_REGULAR = "Regular sale";
    private static final String SALE_RAFFLE = "Raffle";

    private final EventManagementPresenter presenter;
    private final CurrentUser currentUser;
    private String companyId;

    public EventManagementView(EventManagementPresenter presenter, CurrentUser currentUser) {
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
                (!currentUser.hasCompanyPermission(companyId, Permissions.EVENT_EDITING) &&
                 !currentUser.hasCompanyPermission(companyId, Permissions.EDIT_VENUE_LAYOUT) &&
                 !currentUser.hasCompanyPermission(companyId, Permissions.CHANGE_PURCHASE_POLICY) &&
                 !currentUser.hasCompanyPermission(companyId, Permissions.CHANGE_DISCOUNT_POLICY))) {
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

        H2 title = new H2("Event Management");
        title.getStyle().set("margin", "0").set("color", "white").set("flex", "1");

        header.add(title);
        add(header);

        VerticalLayout content = new VerticalLayout();
        content.getStyle().set("padding", "1rem 2rem");

        Result<List<EventDTO>> result = presenter.getCompanyEvents(companyId);
        if (result instanceof Result.Fail<List<EventDTO>> fail) {
            Notification.show(fail.message());
            return;
        }

        List<EventDTO> events = ((Result.Ok<List<EventDTO>>) result).value();
        if (events.isEmpty()) {
            content.add(new Paragraph("No events yet. Create your first event!"));
        } else {
            FlexLayout cards = new FlexLayout();
            cards.getStyle().set("flex-wrap", "wrap").set("gap", "1rem");
            events.forEach(ev -> cards.add(buildEventCard(ev)));
            content.add(cards);
        }
        add(content);

        Button back = new Button("Back", e -> UI.getCurrent().navigate("company/" + companyId));
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        add(back);
    }

    private static boolean hasAnyInvalidField(Component root) {
        if (root instanceof HasValidation hv && hv.isInvalid()) return true;
        return root.getChildren().anyMatch(EventManagementView::hasAnyInvalidField);
    }

    private VerticalLayout buildEventCard(EventDTO event) {
        VerticalLayout card = new VerticalLayout();
        card.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "1rem")
                .set("min-width", "280px")
                .set("max-width", "380px")
                .set("flex", "1");

        H3 name = new H3(event.name);
        name.getStyle().set("margin", "0");

        Paragraph date = new Paragraph(event.startDate + " – " + event.endDate);
        date.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Paragraph location = new Paragraph(event.location);

        HorizontalLayout actions = new HorizontalLayout();
        actions.getStyle()
                .set("border-top", "1px solid var(--lumo-contrast-10pct)")
                .set("padding-top", "0.75rem")
                .set("margin-top", "auto")
                .set("flex-wrap", "wrap")
                .set("width", "100%");

        if (currentUser.hasCompanyPermission(companyId, Permissions.EVENT_EDITING)
                || currentUser.hasCompanyPermission(companyId, Permissions.EDIT_VENUE_LAYOUT)) {
            Button showsBtn = new Button("Show Management", e -> getUI().ifPresent(ui ->
                    ui.navigate("company/" + companyId + "/events/" + event.eventId + "/shows")));
            showsBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            actions.add(showsBtn);
        }

        if (currentUser.hasCompanyPermission(companyId, Permissions.EVENT_EDITING)) {
            Button editBtn = new Button("Edit Event", e -> openEventDialog(event, companyId, presenter, this::loadPage, currentUser));
            editBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);

            Button deleteBtn = new Button("Delete Event", e -> confirmDelete(event));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

            actions.add(editBtn, deleteBtn);
        } else if (currentUser.hasCompanyPermission(companyId, Permissions.CHANGE_PURCHASE_POLICY)
                || currentUser.hasCompanyPermission(companyId, Permissions.CHANGE_DISCOUNT_POLICY)) {
            Button editPoliciesBtn = new Button("Edit Policies", e ->
                    openPoliciesDialog(event, companyId, presenter, this::loadPage, currentUser));
            editPoliciesBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);
            actions.add(editPoliciesBtn);
        }
        if (event.requiresRaffle) {
            card.add(name, EventBrowseView.raffleBadge(), date, location, actions);
        } else {
            card.add(name, date, location, actions);
        }
        return card;
    }

    static void openEventDialog(EventDTO existing, String companyId, EventManagementPresenter presenter, Runnable onSuccess, CurrentUser currentUser) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(existing == null ? "Create Event" : "Edit Event");

        TextField nameField = new TextField("Name");
        TextField typeField = new TextField("Event Type");
        TextField artistField = new TextField("Artist");
        TextField locationField = new TextField("Location");
        DateTimePicker startDate = new DateTimePicker("Start Date");
        DateTimePicker endDate = new DateTimePicker("End Date");
        NumberField minPrice = new NumberField("Min Price");
        NumberField maxPrice = new NumberField("Max Price");

        PurchasePolicyFormComponent purchasePolicyForm = new PurchasePolicyFormComponent();
        PolicyFormComponent discountPolicyForm = new PolicyFormComponent();

        if (existing != null) {
            nameField.setValue(existing.name);
            typeField.setValue(existing.eventTypeName != null ? existing.eventTypeName : "");
            artistField.setValue(existing.artist != null ? existing.artist : "");
            locationField.setValue(existing.location != null ? existing.location : "");
            startDate.setValue(existing.startDate);
            endDate.setValue(existing.endDate);
            minPrice.setValue(existing.minPrice);
            maxPrice.setValue(existing.maxPrice);
            if (existing.purchasePolicy != null) purchasePolicyForm.populate(existing.purchasePolicy);
            if (existing.discountPolicy != null) discountPolicyForm.populate(existing.discountPolicy);
        }

        boolean canManagePolicies = currentUser.hasCompanyPermission(companyId, Permissions.CHANGE_PURCHASE_POLICY)
                || currentUser.hasCompanyPermission(companyId, Permissions.CHANGE_DISCOUNT_POLICY)
                || currentUser.hasCompanyPermission(companyId, Permissions.EVENT_EDITING);

        VerticalLayout form = new VerticalLayout(
                nameField, typeField, artistField, locationField,
                startDate, endDate, minPrice, maxPrice);
        form.setPadding(false);

        if (canManagePolicies) {
            Details purchasePolicySection = new Details("Purchase Policy (optional)", purchasePolicyForm);
            purchasePolicySection.setOpened(existing != null && existing.purchasePolicy != null);

            Details discountPolicySection = new Details("Discount Policy (optional)", discountPolicyForm);
            discountPolicySection.setOpened(existing != null && existing.discountPolicy != null);

            form.add(purchasePolicySection, discountPolicySection);
        }

        // Sale mode + raffle configuration — only when creating an event.
        final boolean creating = existing == null;
        RadioButtonGroup<String> saleMode = new RadioButtonGroup<>();
        DateTimePicker registrationDeadline = new DateTimePicker("Registration deadline");
        IntegerField codeValidityMinutes = new IntegerField("Code validity (minutes)");
        IntegerField numberOfWinners = new IntegerField("Number of winners");
        VerticalLayout raffleConfig = new VerticalLayout(registrationDeadline, numberOfWinners, codeValidityMinutes);
        raffleConfig.setPadding(false);
        raffleConfig.setVisible(false);

        if (creating) {
            saleMode.setLabel("Sale mode");
            saleMode.setItems(SALE_REGULAR, SALE_RAFFLE);
            saleMode.setValue(SALE_REGULAR);
            codeValidityMinutes.setValue(30);
            codeValidityMinutes.setMin(1);
            numberOfWinners.setValue(1);
            numberOfWinners.setMin(1);
            saleMode.addValueChangeListener(e -> raffleConfig.setVisible(SALE_RAFFLE.equals(e.getValue())));
            form.add(saleMode, raffleConfig);
        }

        dialog.add(form);

        Button saveBtn = new Button("Save", e -> {
            EventDTO dto = buildDTO(existing,
                    nameField.getValue(), typeField.getValue(), artistField.getValue(),
                    locationField.getValue(), startDate.getValue(), endDate.getValue(),
                    minPrice.getValue(), maxPrice.getValue(), companyId);

            boolean raffleSelected = creating && SALE_RAFFLE.equals(saleMode.getValue());
            if (raffleSelected) {
                // Validate before the event is created, rather than letting the raffle step fail after.
                if (numberOfWinners.getValue() == null || numberOfWinners.getValue() < 1) {
                    Notification.show("A raffle must award at least one winner.");
                    return;
                }
                // The raffle winner count is independent of event capacity (#439): no capacity check here.
                if (registrationDeadline.getValue() == null
                        || !registrationDeadline.getValue().isAfter(LocalDateTime.now())) {
                    Notification.show("Raffle registration deadline must be in the future.");
                    return;
                }
                if (codeValidityMinutes.getValue() == null || codeValidityMinutes.getValue() < 1) {
                    Notification.show("Code validity must be at least 1 minute.");
                    return;
                }
            }

            // Validate discount policy before saving — buildDTO() sets field-level error state.
            DiscountPolicyDTO discountPolicyDto = discountPolicyForm.buildDTO();
            boolean discountInvalid = hasAnyInvalidField(discountPolicyForm);
            if (discountInvalid) {
                Notification.show("Please fill in all required discount policy fields.");
                return;
            }

            Result<?> result = creating
                    ? presenter.createEvent(companyId, dto)
                    : presenter.editEvent(existing.eventId, dto);

            if (result instanceof Result.Fail<?> fail) {
                Notification.show(fail.message());
                return;
            }

            if (raffleSelected) {
                EventDTO created = ((Result.Ok<EventDTO>) result).value();
                Result<Void> raffleResult = presenter.configureRaffle(
                        created.eventId, registrationDeadline.getValue(),
                        Duration.ofMinutes(codeValidityMinutes.getValue()), numberOfWinners.getValue());
                if (raffleResult instanceof Result.Fail<Void> fail) {
                    Notification.show("Event created, but raffle setup failed: " + fail.message());
                }
            }

            String savedEventId = creating
                    ? ((Result.Ok<EventDTO>) result).value().eventId
                    : existing.eventId;

            PurchasePolicyDTO purchasePolicyDto = purchasePolicyForm.buildDTO();
            if (purchasePolicyDto != null) {
                Result<Void> policyResult = presenter.setEventPurchasePolicy(savedEventId, purchasePolicyDto);
                if (policyResult instanceof Result.Fail<Void> fail) {
                    Notification.show("Event saved, but purchase policy setup failed: " + fail.message());
                }
            }

            if (discountPolicyDto != null) {
                Result<Void> discountResult = presenter.setEventDiscountPolicy(savedEventId, discountPolicyDto);
                if (discountResult instanceof Result.Fail<Void> fail) {
                    Notification.show("Event saved, but discount policy setup failed: " + fail.message());
                }
            }

            dialog.close();
            onSuccess.run();
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    static void openPoliciesDialog(EventDTO existing, String companyId, EventManagementPresenter presenter, Runnable onSuccess, CurrentUser currentUser) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Policies – " + existing.name);

        boolean canEditPurchase = currentUser.hasCompanyPermission(companyId, Permissions.CHANGE_PURCHASE_POLICY);
        boolean canEditDiscount = currentUser.hasCompanyPermission(companyId, Permissions.CHANGE_DISCOUNT_POLICY);

        PurchasePolicyFormComponent purchasePolicyForm = new PurchasePolicyFormComponent();
        PolicyFormComponent discountPolicyForm = new PolicyFormComponent();

        if (existing.purchasePolicy != null) purchasePolicyForm.populate(existing.purchasePolicy);
        if (existing.discountPolicy != null) discountPolicyForm.populate(existing.discountPolicy);

        VerticalLayout form = new VerticalLayout();
        form.setPadding(false);

        if (canEditPurchase) {
            Details purchasePolicySection = new Details("Purchase Policy (optional)", purchasePolicyForm);
            purchasePolicySection.setOpened(existing.purchasePolicy != null);
            form.add(purchasePolicySection);
        }

        if (canEditDiscount) {
            Details discountPolicySection = new Details("Discount Policy (optional)", discountPolicyForm);
            discountPolicySection.setOpened(existing.discountPolicy != null);
            form.add(discountPolicySection);
        }

        dialog.add(form);

        Button saveBtn = new Button("Save", e -> {
            if (canEditPurchase) {
                PurchasePolicyDTO purchasePolicyDto = purchasePolicyForm.buildDTO();
                if (purchasePolicyDto != null) {
                    Result<Void> policyResult = presenter.setEventPurchasePolicy(existing.eventId, purchasePolicyDto);
                    if (policyResult instanceof Result.Fail<Void> fail) {
                        Notification.show("Purchase policy update failed: " + fail.message());
                        return;
                    }
                }
            }
            if (canEditDiscount) {
                DiscountPolicyDTO discountPolicyDto = discountPolicyForm.buildDTO();
                boolean discountInvalid = hasAnyInvalidField(discountPolicyForm);
                if (discountInvalid) {
                    Notification.show("Please fill in all required discount policy fields.");
                    return;
                }
                if (discountPolicyDto != null) {
                    Result<Void> discountResult = presenter.setEventDiscountPolicy(existing.eventId, discountPolicyDto);
                    if (discountResult instanceof Result.Fail<Void> fail) {
                        Notification.show("Discount policy update failed: " + fail.message());
                        return;
                    }
                }
            }
            dialog.close();
            onSuccess.run();
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    private void confirmDelete(EventDTO event) {
        Dialog confirm = new Dialog();
        confirm.setHeaderTitle("Delete Event");
        confirm.add(new Paragraph("Are you sure you want to delete \"" + event.name + "\"?"));

        Button deleteBtn = new Button("Delete", e -> {
            Result<Void> result = presenter.removeEvent(event.eventId);
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

    private static EventDTO buildDTO(EventDTO existing, String name, String type, String artist,
                                     String location, java.time.LocalDateTime start,
                                     java.time.LocalDateTime end, Double minPrice,
                                     Double maxPrice, String companyId) {
        EventDTO dto = new EventDTO();
        dto.eventId = existing != null ? existing.eventId : null;
        dto.name = name;
        dto.eventTypeName = type;
        dto.artist = artist;
        dto.location = location;
        dto.startDate = start;
        dto.endDate = end;
        dto.minPrice = minPrice != null ? minPrice : 0;
        dto.maxPrice = maxPrice != null ? maxPrice : 0;
        dto.productionCompanyName = companyId;
        return dto;
    }
}