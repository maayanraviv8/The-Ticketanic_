package presentation.view;

import application.dto.ProductionCompanyDTO;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import presentation.Brand;
import presentation.CurrentUser;
import presentation.Result;
import presentation.presenter.OpenCompanyPresenter;

@Route(value = "company/open", layout = MainLayout.class)
@PageTitle("Open a Company | Ticketanic")
public class OpenCompanyView extends VerticalLayout implements BeforeEnterObserver {

    private final OpenCompanyPresenter presenter;
    private final CurrentUser currentUser;

    private final TextField companyNameField = new TextField("Company name");
    private final Button openButton = new Button("Open Company");

    public OpenCompanyView(OpenCompanyPresenter presenter, CurrentUser currentUser) {
        this.presenter = presenter;
        this.currentUser = currentUser;

        configurePageBackground();
        add(buildCard());
    }

    private void configurePageBackground() {
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        setPadding(false);
        setSpacing(false);
        getStyle().set("background", "var(--lumo-contrast-5pct)");
    }

    private VerticalLayout buildCard() {
        Image logo = Brand.logo("88px");

        Paragraph subtitle = new Paragraph("Open a new production company");
        subtitle.getStyle()
                .set("margin", "0 0 var(--lumo-space-m) 0")
                .set("color", "var(--lumo-secondary-text-color)");

        companyNameField.setRequiredIndicatorVisible(true);
        companyNameField.setWidthFull();
        companyNameField.setPlaceholder("e.g. Northern Lights Productions");

        openButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        openButton.addClickShortcut(Key.ENTER);
        openButton.addClickListener(e -> submit());
        openButton.setWidthFull();
        openButton.getStyle().set("margin-top", "var(--lumo-space-s)");

        VerticalLayout card = new VerticalLayout(logo, subtitle, companyNameField, openButton);
        card.setAlignItems(FlexComponent.Alignment.STRETCH);
        card.setSpacing(false);
        card.setPadding(true);
        card.setWidth("360px");
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("box-shadow", "var(--lumo-box-shadow-m)")
                .set("padding", "var(--lumo-space-l)")
                .set("gap", "var(--lumo-space-s)");

        card.setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, logo);
        card.setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, subtitle);

        return card;
    }

    private void submit() {
        String name = companyNameField.getValue();
        if (name == null || name.isBlank()) {
            showError("Please enter a company name");
            return;
        }

        String trimmed = name.trim();
        Result<ProductionCompanyDTO> result = presenter.openCompany(trimmed);
        if (result instanceof Result.Ok<ProductionCompanyDTO> ok) {
            Notification success = Notification.show("Company '" + ok.value().name + "' opened");
            success.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            getUI().ifPresent(ui -> ui.navigate("company/" + ok.value().name));
        } else if (result instanceof Result.Fail<ProductionCompanyDTO> fail) {
            showError(fail.message());
        }
    }

    private void showError(String message) {
        Notification n = Notification.show(message);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!currentUser.isLoggedIn()) {
            event.forwardTo("login");
            return;
        }
        if (currentUser.isSuspended()) {
            event.forwardTo("");
        }
    }
}
