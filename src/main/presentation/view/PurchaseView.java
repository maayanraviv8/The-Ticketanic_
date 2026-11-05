package presentation.view;

import application.dto.ActiveOrderDTO;
import application.dto.OrderDTO;
import application.dto.OrderPricePreviewDTO;
import application.dto.PaymentDetailsDTO;
import application.dto.ShowDTO;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import presentation.CurrentUser;
import presentation.Result;
import presentation.presenter.PurchasePresenter;

import java.time.Year;
import java.time.format.DateTimeFormatter;

@Route(value = "purchase/:activeOrderId", layout = MainLayout.class)
@PageTitle("Purchase Tickets | Ticketanic")
@AnonymousAllowed
public class PurchaseView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final PurchasePresenter presenter;
    private final CurrentUser currentUser;

    private String activeOrderId;
    private String editTargetEventId;
    private final VerticalLayout content = new VerticalLayout();
    private final TextField couponField = new TextField();
    private final Button backButton = new Button();

    // Live-updated price display spans (set when summary card is built)
    private Span discountLabelSpan;
    private Span discountValueSpan;
    private Span totalValueSpan;

    public PurchaseView(PurchasePresenter presenter, CurrentUser currentUser) {
        this.presenter = presenter;
        this.currentUser = currentUser;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("background", "var(--lumo-contrast-5pct)");

        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        backButton.addClickListener(e -> {
            if (currentUser.isLoggedIn() && editTargetEventId != null) {
                UI.getCurrent().navigate("event/" + editTargetEventId);
            } else {
                UI.getCurrent().navigate("events");
            }
        });

        content.setPadding(false);
        content.setSpacing(true);

        add(backButton, content);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        event.getRouteParameters().get("activeOrderId").ifPresent(id -> {
            this.activeOrderId = id;
            loadPage(id);
        });
    }

    private void loadPage(String orderId) {
        Result<ActiveOrderDTO> orderResult = presenter.getActiveOrder(orderId);
        if (orderResult instanceof Result.Fail<ActiveOrderDTO> fail) {
            Notification n = Notification.show(fail.message());
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            UI.getCurrent().navigate("events");
            return;
        }

        ActiveOrderDTO order = ((Result.Ok<ActiveOrderDTO>) orderResult).value();
        this.editTargetEventId = order.eventId;
        backButton.setText(currentUser.isLoggedIn() ? "← Edit My Order" : "← Back to Events");

        String eventName = "—";
        Result<String> eventResult = presenter.getEventName(order.eventId);
        if (eventResult instanceof Result.Ok<String> ok) eventName = ok.value();

        String showDate = "—";
        Result<ShowDTO> showResult = presenter.getShow(order.showId);
        if (showResult instanceof Result.Ok<ShowDTO> ok && ok.value().date != null) {
            showDate = ok.value().date.format(DATE_FMT);
        }

        content.removeAll();
        content.add(
                buildSummaryCard(order, eventName, showDate),
                buildCouponCard(order),
                buildCheckoutCard(order)
        );

        // Auto-apply any non-coupon discounts (SIMPLE, CONDITIONAL_TICKETS) on page load
        Result<OrderPricePreviewDTO> autoPreview = presenter.previewCoupon(order.id, null, order.ticketIds.size());
        if (autoPreview instanceof Result.Ok<OrderPricePreviewDTO> previewOk
                && previewOk.value().totalDiscountAmount > 0) {
            OrderPricePreviewDTO preview = previewOk.value();
            discountLabelSpan.setVisible(true);
            discountValueSpan.setVisible(true);
            discountValueSpan.setText(String.format("-$%.2f", preview.totalDiscountAmount));
            totalValueSpan.setText(String.format("$%.2f", preview.finalPrice));
        }
    }

    private Div buildSummaryCard(ActiveOrderDTO order, String eventName, String showDate) {
        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("box-shadow", "var(--lumo-box-shadow-s)")
                .set("padding", "var(--lumo-space-l)");

        H3 title = new H3("Order Summary");
        title.getStyle().set("margin-top", "0").set("margin-bottom", "var(--lumo-space-m)");

        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "auto 1fr")
                .set("gap", "var(--lumo-space-s) var(--lumo-space-l)");

        addGridRow(grid, "Event", eventName);
        addGridRow(grid, "Show", showDate);
        addGridRow(grid, "Tickets", String.valueOf(order.ticketIds.size()));
        addGridRow(grid, "Subtotal", String.format("$%.2f", order.subtotal));

        discountLabelSpan = styledLabel("Discount");
        discountValueSpan = new Span(order.discountAmount > 0 ? String.format("-$%.2f", order.discountAmount) : "");
        discountLabelSpan.setVisible(order.discountAmount > 0);
        discountValueSpan.setVisible(order.discountAmount > 0);
        grid.add(discountLabelSpan, discountValueSpan);

        Span totalLabel = styledLabel("Total");
        totalValueSpan = new Span(String.format("$%.2f", order.totalPrice));
        totalValueSpan.getStyle()
                .set("font-weight", "bold")
                .set("font-size", "var(--lumo-font-size-l)");
        grid.add(totalLabel, totalValueSpan);

        Span expiry = new Span("⏱ Expires at: " + order.expirationTime.format(TIME_FMT));
        expiry.getStyle()
                .set("display", "block")
                .set("margin-top", "var(--lumo-space-m)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-warning-text-color)");

        card.add(title, grid, expiry);
        return card;
    }

    private void addGridRow(Div grid, String labelText, String valueText) {
        grid.add(styledLabel(labelText), new Span(valueText));
    }

    private Span styledLabel(String text) {
        Span label = new Span(text);
        label.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        return label;
    }

    private Div buildCouponCard(ActiveOrderDTO order) {
        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("box-shadow", "var(--lumo-box-shadow-s)")
                .set("padding", "var(--lumo-space-l)");

        H3 title = new H3("Have a Coupon?");
        title.getStyle().set("margin-top", "0").set("margin-bottom", "var(--lumo-space-m)");

        couponField.setPlaceholder("Enter coupon code");
        couponField.setWidth("240px");

        Button applyBtn = new Button("Apply Coupon");
        applyBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        applyBtn.addClickListener(e -> {
            String code = couponField.getValue();
            if (code == null || code.isBlank()) {
                Notification n = Notification.show("Please enter a coupon code.");
                n.addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            Result<OrderPricePreviewDTO> result = presenter.previewCoupon(order.id, code, order.ticketIds.size());
            if (result instanceof Result.Ok<OrderPricePreviewDTO> ok) {
                OrderPricePreviewDTO preview = ok.value();
                if (preview.totalDiscountAmount > 0) {
                    discountLabelSpan.setVisible(true);
                    discountValueSpan.setVisible(true);
                    discountValueSpan.setText(String.format("-$%.2f", preview.totalDiscountAmount));
                    totalValueSpan.setText(String.format("$%.2f", preview.finalPrice));
                    Notification n = Notification.show(String.format("Coupon applied! You save $%.2f", preview.totalDiscountAmount));
                    n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                } else {
                    Notification n = Notification.show("This coupon code is not applicable to your order.");
                    n.addThemeVariants(NotificationVariant.LUMO_WARNING);
                }
            } else if (result instanceof Result.Fail<OrderPricePreviewDTO> fail) {
                Notification n = Notification.show(fail.message());
                n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        HorizontalLayout row = new HorizontalLayout(couponField, applyBtn);
        row.setAlignItems(FlexComponent.Alignment.BASELINE);
        row.setPadding(false);

        card.add(title, row);
        return card;
    }

    private Div buildCheckoutCard(ActiveOrderDTO order) {
        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("box-shadow", "var(--lumo-box-shadow-s)")
                .set("padding", "var(--lumo-space-l)");

        H3 title = new H3("Your Details");
        title.getStyle().set("margin-top", "0").set("margin-bottom", "var(--lumo-space-m)");

        TextField nameField = new TextField("Full Name");
        nameField.setRequired(true);
        nameField.setRequiredIndicatorVisible(true);
        nameField.setWidthFull();

        EmailField emailField = new EmailField("Email");
        emailField.setRequired(true);
        emailField.setRequiredIndicatorVisible(true);
        emailField.setWidthFull();

        TextField phoneField = new TextField("Phone");
        phoneField.setRequired(true);
        phoneField.setRequiredIndicatorVisible(true);
        phoneField.setWidthFull();

        IntegerField ageField = new IntegerField("Age");
        ageField.setMin(1);
        ageField.setMax(120);
        ageField.setStepButtonsVisible(true);
        ageField.setRequiredIndicatorVisible(true);
        ageField.setWidthFull();

        FormLayout detailsForm = new FormLayout(nameField, emailField, phoneField, ageField);
        detailsForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2));

        // ─── Payment details (forwarded to the external payment system) ─── //
        H3 paymentTitle = new H3("Payment Details");
        paymentTitle.getStyle().set("margin-top", "var(--lumo-space-l)").set("margin-bottom", "var(--lumo-space-m)");

        TextField cardNumberField = new TextField("Card Number");
        cardNumberField.setAllowedCharPattern("[0-9]");
        cardNumberField.setMinLength(12);
        cardNumberField.setMaxLength(19);
        cardNumberField.setRequiredIndicatorVisible(true);
        cardNumberField.setWidthFull();

        IntegerField monthField = new IntegerField("Expiry Month");
        monthField.setMin(1);
        monthField.setMax(12);
        monthField.setRequiredIndicatorVisible(true);
        monthField.setWidthFull();

        IntegerField yearField = new IntegerField("Expiry Year");
        yearField.setMin(Year.now().getValue());
        yearField.setMax(Year.now().getValue() + 20);
        yearField.setRequiredIndicatorVisible(true);
        yearField.setWidthFull();

        TextField holderField = new TextField("Card Holder");
        holderField.setRequiredIndicatorVisible(true);
        holderField.setWidthFull();

        TextField cvvField = new TextField("CVV");
        cvvField.setAllowedCharPattern("[0-9]");
        cvvField.setMinLength(3);
        cvvField.setMaxLength(4);
        cvvField.setRequiredIndicatorVisible(true);
        cvvField.setWidthFull();

        TextField payerIdField = new TextField("Payer ID");
        payerIdField.setAllowedCharPattern("[0-9]");
        payerIdField.setRequiredIndicatorVisible(true);
        payerIdField.setWidthFull();

        ComboBox<String> currencyField = new ComboBox<>("Currency");
        currencyField.setItems("USD", "EUR", "GBP", "ILS");
        currencyField.setValue("USD");
        currencyField.setRequiredIndicatorVisible(true);
        currencyField.setWidthFull();

        FormLayout paymentForm = new FormLayout(cardNumberField, holderField, monthField, yearField,
                cvvField, payerIdField, currencyField);
        paymentForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2));

        Button checkoutBtn = new Button("Complete Purchase");
        checkoutBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        checkoutBtn.getStyle().set("margin-top", "var(--lumo-space-m)");

        checkoutBtn.addClickListener(e -> {
            if (nameField.isEmpty() || emailField.isEmpty() || phoneField.isEmpty() || ageField.isEmpty()) {
                Notification n = Notification.show("Please fill in all required fields.");
                n.addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            String paymentError = validatePaymentFields(cardNumberField, monthField, yearField,
                    holderField, cvvField, payerIdField, currencyField);
            if (paymentError != null) {
                Notification n = Notification.show(paymentError);
                n.addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            String couponCode = couponField.getValue();
            if (couponCode != null && couponCode.isBlank()) {
                couponCode = null;
            }
            PaymentDetailsDTO paymentDetails = new PaymentDetailsDTO(
                    cardNumberField.getValue(),
                    String.valueOf(monthField.getValue()),
                    String.valueOf(yearField.getValue()),
                    holderField.getValue(),
                    cvvField.getValue(),
                    currencyField.getValue(),
                    payerIdField.getValue());
            checkoutBtn.setEnabled(false);
            Result<OrderDTO> result = presenter.checkout(
                    order.id,
                    nameField.getValue(),
                    emailField.getValue(),
                    phoneField.getValue(),
                    ageField.getValue(),
                    order.ticketIds.size(),
                    couponCode,
                    paymentDetails);

            if (result instanceof Result.Ok<OrderDTO> ok) {
                showConfirmation(ok.value());
            } else if (result instanceof Result.Fail<OrderDTO> fail) {
                // The reservation survives a failed payment, so keep every entered value in place
                // and let the user correct whichever field was wrong and retry without re-typing
                // the rest. The next click re-reads the fields, so the corrected value is sent.
                // (Crucially we do NOT clear the card number — doing so would blank a field the
                // user left untouched and make the retry fail again.)
                checkoutBtn.setEnabled(true);
                Notification n = Notification.show(
                        "Payment failed. Please check your payment details and try again.");
                n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        card.add(title, detailsForm, paymentTitle, paymentForm, checkoutBtn);
        return card;
    }

    /**
     * Validates the card / payer inputs. Returns an error message to show the user,
     * or {@code null} when every field is acceptable. Format constraints (digits only,
     * length, month/year ranges) are also enforced on the fields themselves; this guard
     * catches empty values and the combined case.
     */
    private String validatePaymentFields(TextField cardNumber, IntegerField month, IntegerField year,
                                         TextField holder, TextField cvv, TextField payerId,
                                         ComboBox<String> currency) {
        if (cardNumber.isEmpty() || month.isEmpty() || year.isEmpty() || holder.isEmpty()
                || cvv.isEmpty() || payerId.isEmpty() || currency.isEmpty()) {
            return "Please fill in all payment details.";
        }
        if (cardNumber.getValue().length() < 12 || cardNumber.getValue().length() > 19) {
            return "Card number must be 12–19 digits.";
        }
        if (cvv.getValue().length() < 3 || cvv.getValue().length() > 4) {
            return "CVV must be 3 or 4 digits.";
        }
        return null;
    }

    private void showConfirmation(OrderDTO order) {
        removeAll();

        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("box-shadow", "var(--lumo-box-shadow-s)")
                .set("padding", "var(--lumo-space-xl)")
                .set("text-align", "center");

        H2 heading = new H2("Purchase Complete!");
        heading.getStyle().set("color", "var(--lumo-success-color)").set("margin-top", "0");

        Paragraph details = new Paragraph(
                "Order #" + order.getOrderId() + " · Total: $" + String.format("%.2f", order.getFinalPrice()));
        details.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Button homeBtn = new Button("Return to Events", e -> UI.getCurrent().navigate("events"));
        homeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        card.add(heading, details, homeBtn);
        add(card);
    }
}
