package presentation.view;

import application.dto.PurchasePolicyDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import presentation.presenter.PurchasePolicyPresenter;

import java.util.ArrayList;
import java.util.List;


public class PurchasePolicyFormComponent extends VerticalLayout {

    private final ComboBox<String> typeSelect    = new ComboBox<>("Policy Type");
    private final IntegerField     minAgeField   = new IntegerField("Minimum Age");
    private final IntegerField     minTicketsFld = new IntegerField("Minimum Tickets");
    private final IntegerField     maxTicketsFld = new IntegerField("Maximum Tickets");
    private final VerticalLayout   childrenArea  = new VerticalLayout();
    private final List<PurchasePolicyFormComponent> children = new ArrayList<>();

    public PurchasePolicyFormComponent() {
        setPadding(true);
        getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("margin-bottom", "0.5rem");

        typeSelect.setItems(
                PurchasePolicyPresenter.TYPE_MIN_AGE,
                PurchasePolicyPresenter.TYPE_MIN_TICKETS,
                PurchasePolicyPresenter.TYPE_MAX_TICKETS,
                PurchasePolicyPresenter.TYPE_AND,
                PurchasePolicyPresenter.TYPE_OR);
        typeSelect.setPlaceholder("Select type…");

        minAgeField.setMin(0);
        minTicketsFld.setMin(0);
        maxTicketsFld.setMin(1);

        minAgeField.setVisible(false);
        minTicketsFld.setVisible(false);
        maxTicketsFld.setVisible(false);
        childrenArea.setVisible(false);
        childrenArea.setPadding(false);

        add(typeSelect, minAgeField, minTicketsFld, maxTicketsFld, childrenArea);

        typeSelect.addValueChangeListener(e -> updateFields(e.getValue()));
    }


    private void updateFields(String type) {
        minAgeField.setVisible(false);
        minTicketsFld.setVisible(false);
        maxTicketsFld.setVisible(false);
        childrenArea.setVisible(false);
        childrenArea.removeAll();
        children.clear();

        if (type == null) return;

        switch (type) {
            case PurchasePolicyPresenter.TYPE_MIN_AGE     -> minAgeField.setVisible(true);
            case PurchasePolicyPresenter.TYPE_MIN_TICKETS -> minTicketsFld.setVisible(true);
            case PurchasePolicyPresenter.TYPE_MAX_TICKETS -> maxTicketsFld.setVisible(true);
            case PurchasePolicyPresenter.TYPE_AND,
                 PurchasePolicyPresenter.TYPE_OR -> {
                childrenArea.setVisible(true);

                Button addChild = new Button("+ Add Sub-Policy", ev -> {
                    PurchasePolicyFormComponent child = new PurchasePolicyFormComponent();
                    children.add(child);
                    childrenArea.addComponentAtIndex(childrenArea.getComponentCount() - 1, child);
                });
                addChild.addThemeVariants(ButtonVariant.LUMO_SMALL);

                childrenArea.add(addChild);
            }
        }
    }

    public PurchasePolicyDTO buildDTO() {
        String type = typeSelect.getValue();
        if (type == null) return null;

        PurchasePolicyDTO dto = new PurchasePolicyDTO();
        dto.type = type;

        switch (type) {
            case PurchasePolicyPresenter.TYPE_MIN_AGE -> {
                if (minAgeField.getValue() == null) return null;
                dto.minAge = minAgeField.getValue();
            }
            case PurchasePolicyPresenter.TYPE_MIN_TICKETS -> {
                if (minTicketsFld.getValue() == null) return null;
                dto.minTickets = minTicketsFld.getValue();
            }
            case PurchasePolicyPresenter.TYPE_MAX_TICKETS -> {
                if (maxTicketsFld.getValue() == null) return null;
                dto.maxTickets = maxTicketsFld.getValue();
            }
            case PurchasePolicyPresenter.TYPE_AND,
                 PurchasePolicyPresenter.TYPE_OR -> {
                if (children.size() < 2) return null;
                List<PurchasePolicyDTO> childDTOs = new ArrayList<>();
                for (PurchasePolicyFormComponent child : children) {
                    PurchasePolicyDTO childDTO = child.buildDTO();
                    if (childDTO == null) return null;
                    childDTOs.add(childDTO);
                }
                dto.children = childDTOs;
            }
        }

        return dto;
    }

    public void populate(PurchasePolicyDTO dto) {
        if (dto == null || dto.type == null) return;

        typeSelect.setValue(dto.type.toUpperCase());

        switch (dto.type.toUpperCase()) {
            case PurchasePolicyPresenter.TYPE_MIN_AGE -> {
                if (dto.minAge != null) minAgeField.setValue(dto.minAge);
            }
            case PurchasePolicyPresenter.TYPE_MIN_TICKETS -> {
                if (dto.minTickets != null) minTicketsFld.setValue(dto.minTickets);
            }
            case PurchasePolicyPresenter.TYPE_MAX_TICKETS -> {
                if (dto.maxTickets != null) maxTicketsFld.setValue(dto.maxTickets);
            }
            case PurchasePolicyPresenter.TYPE_AND,
                 PurchasePolicyPresenter.TYPE_OR -> {
                if (dto.children != null) {
                    for (PurchasePolicyDTO childDto : dto.children) {
                        PurchasePolicyFormComponent child = new PurchasePolicyFormComponent();
                        child.populate(childDto);
                        children.add(child);
                        childrenArea.addComponentAtIndex(childrenArea.getComponentCount() - 1, child);
                    }
                }
            }
        }
    }
}