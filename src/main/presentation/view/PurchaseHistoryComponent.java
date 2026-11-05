package presentation.view;

import application.dto.OrderDTO;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;
import java.util.Map;

public class PurchaseHistoryComponent {

    public static Component buildHistory(Map<String, List<OrderDTO>> groupedOrders) {
        VerticalLayout root = new VerticalLayout();
        root.setSpacing(false);
        root.setPadding(false);

        if (groupedOrders == null || groupedOrders.isEmpty()) {
            root.add(new Paragraph("No purchase history found."));
            return root;
        }

        for (Map.Entry<String, List<OrderDTO>> entry : groupedOrders.entrySet()) {
            VerticalLayout group = new VerticalLayout();
            group.setSpacing(false);
            group.setPadding(false);
            group.getStyle()
                    .set("border-bottom", "1px solid var(--lumo-contrast-20pct)")
                    .set("padding-bottom", "1rem")
                    .set("margin-bottom", "1rem");

            H3 groupTitle = new H3(entry.getKey());
            groupTitle.getStyle().set("margin", "0 0 0.5rem 0");
            group.add(groupTitle);

            for (OrderDTO order : entry.getValue()) {
                Span orderLine = new Span("Order Number: " + order.getOrderId()
                        + " | Tickets: " + order.getTicketIds().size()
                        + " | Price: $" + String.format("%.2f", order.getFinalPrice())
                        + " | Date: " + (order.getPurchaseTime() != null ? order.getPurchaseTime().toString() : "N/A"));
                orderLine.getStyle().set("display", "block").set("padding", "2px 0").set("color", "var(--lumo-secondary-text-color)");
                group.add(orderLine);
            }

            root.add(group);
        }

        return root;
    }

    public static Component buildNestedHistory(Map<String, Map<String, List<OrderDTO>>> nested) {
        VerticalLayout root = new VerticalLayout();
        root.setSpacing(false);
        root.setPadding(false);

        if (nested == null || nested.isEmpty()) {
            root.add(new Paragraph("No purchase history found."));
            return root;
        }

        for (Map.Entry<String, Map<String, List<OrderDTO>>> companyEntry : nested.entrySet()) {
            VerticalLayout companyBlock = new VerticalLayout();
            companyBlock.setSpacing(false);
            companyBlock.setPadding(false);
            companyBlock.getStyle()
                    .set("border-bottom", "2px solid var(--lumo-contrast-30pct)")
                    .set("padding-bottom", "1rem")
                    .set("margin-bottom", "1.5rem");

            H2 companyTitle = new H2(companyEntry.getKey());
            companyTitle.getStyle().set("margin", "0 0 0.5rem 0");
            companyBlock.add(companyTitle);

            companyBlock.add(buildHistory(companyEntry.getValue()));
            root.add(companyBlock);
        }

        return root;
    }
}