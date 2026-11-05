package presentation.view;

import application.dto.EventDTO;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import presentation.presenter.EventBrowsePresenter;
import presentation.Result;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "events", layout = MainLayout.class)
@PageTitle("Browse Events | Ticketanic")
@AnonymousAllowed
public class EventBrowseView extends VerticalLayout {

    private final EventBrowsePresenter presenter;

    // Filter fields
    private final TextField keywordsField = new TextField("Keywords");
    private final TextField categoryField = new TextField("Category");
    private final TextField locationField = new TextField("Location");
    private final TextField companyNameField = new TextField("Production Company");
    private final DateTimePicker startDateField = new DateTimePicker("Start Date");
    private final DateTimePicker endDateField = new DateTimePicker("End Date");
    private final NumberField minPriceField = new NumberField("Min Price");
    private final NumberField maxPriceField = new NumberField("Max Price");

    // Results container
    private final FlexLayout resultsContainer = new FlexLayout();

    public EventBrowseView(EventBrowsePresenter presenter) {
        this.presenter = presenter;
        
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("background", "var(--lumo-contrast-5pct)");
        
        buildHero();
        buildFilterPanel();
        buildResultsArea();
        
        // Initial search to populate list
        searchEvents();
    }

    private void buildHero() {
        VerticalLayout hero = new VerticalLayout();
        hero.setWidthFull();
        hero.setAlignItems(FlexComponent.Alignment.CENTER);
        hero.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        hero.getStyle()
                .set("background", "linear-gradient(135deg, var(--lumo-primary-color) 0%, var(--lumo-primary-color-50pct) 100%)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("padding", "var(--lumo-space-xl)");

        H2 title = new H2("Discover Events");
        title.getStyle()
                .set("color", "white")
                .set("margin", "0")
                .set("font-size", "2.5rem");

        Paragraph subtitle = new Paragraph("Find your next experience");
        subtitle.getStyle()
                .set("color", "var(--lumo-tint-80pct)")
                .set("margin-top", "var(--lumo-space-s)")
                .set("font-size", "1.2rem");

        hero.add(title, subtitle);
        add(hero);
    }

    private void buildFilterPanel() {
        Div filterCard = new Div();
        filterCard.setWidthFull();
        filterCard.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("box-shadow", "var(--lumo-box-shadow-s)")
                .set("padding", "var(--lumo-space-l)");

        FormLayout formLayout = new FormLayout();
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2),
                new FormLayout.ResponsiveStep("800px", 4)
        );

        keywordsField.setPlaceholder("e.g. Concert, Play...");
        minPriceField.setPlaceholder("0.00");
        maxPriceField.setPlaceholder("1000.00");

        formLayout.add(
                keywordsField, categoryField, locationField, companyNameField,
                startDateField, endDateField, minPriceField, maxPriceField
        );

        Button searchButton = new Button("Search");
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> searchEvents());

        Button clearButton = new Button("Clear");
        clearButton.addClickListener(e -> clearFilters());

        HorizontalLayout buttonsLayout = new HorizontalLayout(searchButton, clearButton);
        buttonsLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttonsLayout.setWidthFull();
        buttonsLayout.getStyle().set("margin-top", "var(--lumo-space-m)");

        filterCard.add(formLayout, buttonsLayout);
        add(filterCard);
    }

    private void buildResultsArea() {
        resultsContainer.setWidthFull();
        resultsContainer.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        resultsContainer.getStyle()
                .set("gap", "var(--lumo-space-l)")
                .set("padding-top", "var(--lumo-space-m)");
        
        add(resultsContainer);
        expand(resultsContainer);
    }

    private void searchEvents() {
        Result<List<EventDTO>> result = presenter.searchEvents(
                companyNameField.getValue(),
                keywordsField.getValue(),
                categoryField.getValue(),
                startDateField.getValue(),
                endDateField.getValue(),
                locationField.getValue(),
                minPriceField.getValue(),
                maxPriceField.getValue()
        );

        if (result instanceof Result.Ok<List<EventDTO>> ok) {
            displayResults(ok.value());
        } else if (result instanceof Result.Fail<List<EventDTO>> fail) {
            Notification n = Notification.show(fail.message());
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void displayResults(List<EventDTO> events) {
        resultsContainer.removeAll();

        if (events == null || events.isEmpty()) {
            Paragraph noResults = new Paragraph("No events found matching your criteria.");
            noResults.getStyle().set("color", "var(--lumo-secondary-text-color)");
            resultsContainer.add(noResults);
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

        for (EventDTO event : events) {
            Div card = new Div();
            card.getStyle()
                    .set("background", "var(--lumo-base-color)")
                    .set("border-radius", "var(--lumo-border-radius-m)")
                    .set("box-shadow", "var(--lumo-box-shadow-s)")
                    .set("padding", "var(--lumo-space-l)")
                    .set("display", "flex")
                    .set("flex-direction", "column")
                    .set("width", "300px")
                    .set("flex-grow", "1")
                    .set("max-width", "400px")
                    .set("transition", "transform 0.2s, box-shadow 0.2s");
            
            // Hover effect trick via css pseudo elements or simply inline JS/EventListeners, 
            // but for simple Vaadin inline styles we can just set base styling well.
            // Vaadin component style add/remove on hover requires listeners, we'll keep it static for simplicity.

            H3 title = new H3(event.getName());
            title.getStyle().set("margin-top", "0").set("margin-bottom", "var(--lumo-space-xs)");

            Span typeSpan = new Span(event.getEventTypeName() != null ? event.getEventTypeName() : "Uncategorized");
            typeSpan.getStyle()
                    .set("font-size", "var(--lumo-font-size-xs)")
                    .set("background", "var(--lumo-contrast-10pct)")
                    .set("padding", "2px var(--lumo-space-s)")
                    .set("border-radius", "var(--lumo-border-radius-s)")
                    .set("display", "inline-block")
                    .set("margin-bottom", "var(--lumo-space-m)");

            Paragraph date = new Paragraph("Start: " + (event.getStartDate() != null ? event.getStartDate().format(formatter) : "N/A"));
            date.getStyle().set("margin", "0").set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");

            Paragraph company = new Paragraph("By: " + event.getProductionCompanyName());
            company.getStyle().set("margin", "0").set("margin-bottom", "var(--lumo-space-m)").set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");

            Button detailsButton = new Button("View Details");
            detailsButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_CONTRAST);
            detailsButton.setWidthFull();
            detailsButton.getStyle().set("margin-top", "auto"); // push to bottom
            
            detailsButton.addClickListener(e -> {
                UI.getCurrent().navigate("event/" + event.getEventId());
            });

            if (event.requiresRaffle) {
                card.add(title, typeSpan, raffleBadge(), date, company, detailsButton);
            } else {
                card.add(title, typeSpan, date, company, detailsButton);
            }
            resultsContainer.add(card);
        }
    }

    /** A small amber pill marking an event as raffle-gated. */
    static Span raffleBadge() {
        Span badge = new Span("🎟 Raffle");
        badge.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("background", "var(--lumo-primary-color)")
                .set("color", "white")
                .set("padding", "2px var(--lumo-space-s)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("display", "inline-block")
                .set("width", "fit-content")
                .set("margin-bottom", "var(--lumo-space-m)");
        return badge;
    }

    private void clearFilters() {
        keywordsField.clear();
        categoryField.clear();
        locationField.clear();
        companyNameField.clear();
        startDateField.clear();
        endDateField.clear();
        minPriceField.clear();
        maxPriceField.clear();
        searchEvents();
    }
}
