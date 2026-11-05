package presentation.view;

import application.dto.DiscountPolicyDTO;
import application.dto.EventDTO;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import presentation.*;
import presentation.presenter.DiscountPolicyPresenter;

import java.util.List;
import java.util.function.Consumer;

@Route(value = "company/:companyId/discount-policy", layout = MainLayout.class)
@PageTitle("Discount Policy")
public class DiscountPolicyView extends VerticalLayout implements BeforeEnterObserver {

    private final DiscountPolicyPresenter presenter;
    private final CurrentUser currentUser;
    private String companyId;

    public DiscountPolicyView(DiscountPolicyPresenter presenter, CurrentUser currentUser) {
        this.presenter = presenter;
        this.currentUser = currentUser;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = event.getRouteParameters().get("companyId").orElse("");
        if (!currentUser.isLoggedIn()) {
            event.forwardTo("login");
            return;
        }
        loadPage();
    }

    private void loadPage() {
        removeAll();

        HorizontalLayout header = new HorizontalLayout();
        header.getStyle()
                .set("background", "linear-gradient(135deg, var(--lumo-primary-color), #141d2e)")
                .set("padding", "2rem").set("width", "100%").set("align-items", "center");
        H2 title = new H2("Discount Policy — " + companyId);
        title.getStyle().set("margin", "0").set("color", "white");
        header.add(title);
        add(header);

        VerticalLayout content = new VerticalLayout();
        content.getStyle().set("padding", "2rem").set("max-width", "800px");
        content.add(buildCompanyPolicySection());
        content.add(new Hr());
        content.add(buildEventsPolicySection());
        add(content);

        Button back = new Button("Back", e -> UI.getCurrent().navigate("company/" + companyId));
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        back.getStyle().set("margin", "0 2rem 2rem");
        add(back);
        setHorizontalComponentAlignment(Alignment.START, back);
    }

    private Component buildCompanyPolicySection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H3("Company Discount Policy"));

        Result<DiscountPolicyDTO> result = presenter.getCompanyPolicy(companyId);
        DiscountPolicyDTO existing = (result instanceof Result.Ok<DiscountPolicyDTO> ok) ? ok.value() : null;

        if (existing == null) {
            section.add(new Paragraph("No discount policy currently exists for this company."));
            Button addBtn = new Button("Add Policy", e ->
                    openPolicyDialog("Company Policy", null, dto -> {
                        Result<Void> res = presenter.setCompanyPolicy(companyId, dto);
                        handleResult(res, "Company policy saved.");
                    }));
            section.add(addBtn);
        } else {
            section.add(new Paragraph("Current policy type: " + existing.type));
            Button editBtn = new Button("Edit Policy", e ->
                    openPolicyDialog("Company Policy", existing, dto -> {
                        Result<Void> res = presenter.setCompanyPolicy(companyId, dto);
                        handleResult(res, "Company policy saved.");
                    }));
            Button removeBtn = new Button("Remove Policy", e ->
                    handleResult(presenter.removeCompanyPolicy(companyId), "Policy removed."));
            removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            section.add(new HorizontalLayout(editBtn, removeBtn));
        }

        return section;
    }

    private Component buildEventsPolicySection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H3("Event Discount Policies"));

        Result<List<EventDTO>> eventsResult = presenter.getCompanyEvents(companyId);
        if (eventsResult instanceof Result.Fail<List<EventDTO>> fail) {
            section.add(new Paragraph("Could not load events: " + fail.message()));
            return section;
        }

        List<EventDTO> events = ((Result.Ok<List<EventDTO>>) eventsResult).value();
        if (events.isEmpty()) {
            section.add(new Paragraph("No events found for this company."));
            return section;
        }

        for (EventDTO event : events) {
            section.add(buildEventRow(event));
        }

        return section;
    }

    private Component buildEventRow(EventDTO event) {
        HorizontalLayout row = new HorizontalLayout();
        row.setAlignItems(Alignment.CENTER);
        row.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "0.75rem 1rem")
                .set("width", "100%");

        Result<DiscountPolicyDTO> policyResult = presenter.getEventPolicy(event.getEventId());
        DiscountPolicyDTO existing = (policyResult instanceof Result.Ok<DiscountPolicyDTO> ok) ? ok.value() : null;

        Span eventName = new Span(event.name);
        eventName.getStyle().set("flex", "1").set("font-weight", "bold");

        Span status = new Span(existing != null ? "Policy: " + existing.type : "No policy");
        status.getStyle()
                .set("flex", "1")
                .set("color", existing != null
                        ? "var(--lumo-success-color)"
                        : "var(--lumo-secondary-text-color)");

        Button actionBtn = new Button(existing != null ? "Edit" : "Add", e ->
                openPolicyDialog(event.name + " Policy", existing, dto -> {
                    Result<Void> res = presenter.setEventPolicy(event.getEventId(), dto);
                    handleResult(res, "Event policy saved.");
                }));
        actionBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);

        if (existing != null) {
            Button removeBtn = new Button("Remove", e ->
                    handleResult(presenter.removeEventPolicy(event.getEventId()), "Event policy removed."));
            removeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            row.add(eventName, status, actionBtn, removeBtn);
        } else {
            row.add(eventName, status, actionBtn);
        }
        return row;
    }

    private void openPolicyDialog(String title, DiscountPolicyDTO existing, Consumer<DiscountPolicyDTO> onSave) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        dialog.setWidth("650px");

        PolicyFormComponent form = new PolicyFormComponent();
        if (existing != null) form.populate(existing);
        dialog.add(form);

        Button saveBtn = new Button("Save", e -> {
            DiscountPolicyDTO dto = form.buildDTO();
            if (dto == null) {
                Notification.show("Please fill in all required fields.");
                return;
            }
            onSave.accept(dto);
            dialog.close();
            loadPage();
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    private void handleResult(Result<Void> result, String successMessage) {
        if (result instanceof Result.Ok) {
            Notification.show(successMessage);
        } else {
            Notification.show(((Result.Fail<Void>) result).message());
        }
    }
}