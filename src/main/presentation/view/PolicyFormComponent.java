package presentation.view;

import application.dto.DiscountPolicyDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;

import java.util.ArrayList;
import java.util.List;

public class PolicyFormComponent extends VerticalLayout {
    private final ComboBox<String> typeSelect = new ComboBox<>("Policy Type");
    private final NumberField percentageField = new NumberField("Discount %");
    private final TextField couponCodeField = new TextField("Coupon Code");
    private final DatePicker validUntilField = new DatePicker("Valid Until");
    private final IntegerField minTicketsField = new IntegerField("Minimum Tickets");
    private final VerticalLayout childrenArea = new VerticalLayout();
    private final List<PolicyFormComponent> children = new ArrayList<>();

    public PolicyFormComponent() {
        setPadding(true);
        getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("margin-bottom", "0.5rem");

        typeSelect.setItems("SIMPLE", "CONDITIONAL_TICKETS", "COUPON", "MAX", "SUM");
        typeSelect.setPlaceholder("Select type...");
        add(typeSelect);

        percentageField.setMin(0);
        percentageField.setMax(100);
        percentageField.setSuffixComponent(new com.vaadin.flow.component.html.Span("%"));
        percentageField.setVisible(false);
        couponCodeField.setVisible(false);
        validUntilField.setVisible(false);
        minTicketsField.setVisible(false);
        childrenArea.setVisible(false);
        childrenArea.setPadding(false);

        add(percentageField, couponCodeField, validUntilField, minTicketsField, childrenArea);

        typeSelect.addValueChangeListener(e -> updateFieldVisibility(e.getValue()));
    }

    private void updateFieldVisibility(String type) {
        percentageField.setVisible(false);
        couponCodeField.setVisible(false);
        validUntilField.setVisible(false);
        validUntilField.setRequired(false);
        validUntilField.setInvalid(false);
        minTicketsField.setVisible(false);
        childrenArea.setVisible(false);
        childrenArea.removeAll();
        children.clear();

        if (type == null) return;

        switch (type) {
            case "SIMPLE" -> percentageField.setVisible(true);
            case "CONDITIONAL_TICKETS" -> {
                percentageField.setVisible(true);
                minTicketsField.setVisible(true);
            }
            case "COUPON" -> {
                percentageField.setVisible(true);
                couponCodeField.setVisible(true);
                validUntilField.setRequired(true);
                validUntilField.setVisible(true);
            }
            case "MAX", "SUM" -> {
                childrenArea.setVisible(true);
                Button addChild = new Button("+ Add Sub-Policy", ev -> {
                    PolicyFormComponent child = new PolicyFormComponent();
                    children.add(child);
                    // Insert before the Add button
                    childrenArea.addComponentAtIndex(childrenArea.getComponentCount() - 1, child);
                });
                addChild.addThemeVariants(ButtonVariant.LUMO_SMALL);
                childrenArea.add(addChild);
            }
        }
    }

    public DiscountPolicyDTO buildDTO() {
        String type = typeSelect.getValue();
        if (type == null) return null;

        DiscountPolicyDTO dto = new DiscountPolicyDTO();
        dto.type = type;

        switch (type) {
            case "SIMPLE" -> {
                if (percentageField.getValue() == null) return null;
                dto.percentage = percentageField.getValue();
            }
            case "CONDITIONAL_TICKETS" -> {
                if (percentageField.getValue() == null || minTicketsField.getValue() == null) return null;
                dto.percentage = percentageField.getValue();
                dto.minTickets = minTicketsField.getValue();
            }
            case "COUPON" -> {
                if (percentageField.getValue() == null || couponCodeField.isEmpty()) return null;
                if (validUntilField.getValue() == null) {
                    validUntilField.setInvalid(true);
                    validUntilField.setErrorMessage("Expiration date is required for a coupon.");
                    return null;
                }
                validUntilField.setInvalid(false);
                dto.percentage = percentageField.getValue();
                dto.couponCode = couponCodeField.getValue();
                dto.validUntil = validUntilField.getValue().atStartOfDay();
            }
            case "MAX", "SUM" -> {
                List<DiscountPolicyDTO> childDTOs = new ArrayList<>();
                for (PolicyFormComponent child : children) {
                    DiscountPolicyDTO childDTO = child.buildDTO();
                    if (childDTO == null) return null;
                    childDTOs.add(childDTO);
                }
                dto.children = childDTOs;
            }
        }

        return dto;
    }

    public void populate(DiscountPolicyDTO dto) {
        if (dto == null || dto.type == null) return;

        typeSelect.setValue(dto.type);

        switch (dto.type.toUpperCase()) {
            case "SIMPLE" -> percentageField.setValue(dto.percentage);
            case "CONDITIONAL_TICKETS" -> {
                percentageField.setValue(dto.percentage);
                if (dto.minTickets != null) minTicketsField.setValue(dto.minTickets);
            }
            case "COUPON" -> {
                percentageField.setValue(dto.percentage);
                couponCodeField.setValue(dto.couponCode != null ? dto.couponCode : "");
                if (dto.validUntil != null) validUntilField.setValue(dto.validUntil.toLocalDate());
            }
            case "MAX", "SUM" -> {
                if (dto.children != null) {
                    for (DiscountPolicyDTO childDto : dto.children) {
                        PolicyFormComponent child = new PolicyFormComponent();
                        child.populate(childDto);
                        children.add(child);
                        childrenArea.addComponentAtIndex(childrenArea.getComponentCount() - 1, child);
                    }
                }
            }
        }
    }
}
