package presentation.presenter;

import application.dto.RoleDTO;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class HierarchyComponent {

//when needs permission
    public static Component buildTree(Map<String, List<String>> hierarchy) {
        VerticalLayout root = new VerticalLayout();
        root.setSpacing(false);
        root.setPadding(false);

        if (hierarchy == null || hierarchy.isEmpty()) {
            root.add(new Paragraph("No hierarchy data."));
            return root;
        }

        Set<String> allAppointed = hierarchy.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        hierarchy.keySet().stream()
                .filter(m -> !allAppointed.contains(m))
                .forEach(rootMember -> buildNode(root, rootMember, hierarchy, 0));

        return root;
    }

//for admin
    public static Component buildTree(List<RoleDTO> roles) {
        VerticalLayout root = new VerticalLayout();
        root.setSpacing(false);
        root.setPadding(false);

        if (roles == null || roles.isEmpty()) {
            root.add(new Paragraph("No hierarchy data."));
            return root;
        }

        RoleDTO founder = roles.stream()
                .filter(r -> "Founder".equals(r.roleType))
                .findFirst().orElse(null);

        if (founder == null) {
            root.add(new Paragraph("No founder found."));
            return root;
        }

        buildNode(root, founder, roles, 0);
        return root;
    }

    private static void buildNode(VerticalLayout container, String memberId, Map<String, List<String>> hierarchy, int depth) {
        Span label = new Span(memberId);
        label.getStyle()
                .set("padding-left", (depth * 24) + "px")
                .set("display", "block")
                .set("padding-top", "4px")
                .set("padding-bottom", "4px");
        container.add(label);

        hierarchy.getOrDefault(memberId, List.of()).forEach(child -> buildNode(container, child, hierarchy, depth + 1));
    }

    private static void buildNode(VerticalLayout container, RoleDTO role,
                                  List<RoleDTO> all, int depth) {
        Span label = new Span(role.memberId + " (" + role.roleType + ")");
        label.getStyle()
                .set("padding-left", (depth * 24) + "px")
                .set("display", "block")
                .set("padding-top", "4px")
                .set("padding-bottom", "4px");
        container.add(label);

        all.stream()
                .filter(r -> role.memberId.equals(r.appointerId))
                .forEach(child -> buildNode(container, child, all, depth + 1));
    }
}