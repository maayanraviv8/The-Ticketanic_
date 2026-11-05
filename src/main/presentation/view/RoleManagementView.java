package presentation.view;

import application.dto.RoleDTO;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import domain.Aggregates.productioncompany.Permissions;
import presentation.CurrentUser;
import presentation.Result;
import presentation.presenter.RoleManagementPresenter;

import java.util.*;
import java.util.stream.Collectors;

@Route(value = "company/:companyId/roles", layout = MainLayout.class)
@PageTitle("Role Management")
public class RoleManagementView extends VerticalLayout implements BeforeEnterObserver {

    private final RoleManagementPresenter presenter;
    private final CurrentUser currentUser;
    private String companyId;

    public RoleManagementView(RoleManagementPresenter presenter, CurrentUser currentUser) {
        this.presenter = presenter;
        this.currentUser = currentUser;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!currentUser.isLoggedIn()) {
            event.forwardTo("login");
            return;
        }
        companyId = event.getRouteParameters().get("companyId").orElse(null);
        if (companyId == null) {
            event.forwardTo("");
            return;
        }
        boolean canAppoint = currentUser.hasCompanyPermission(companyId, Permissions.APPOINT_OWNERS)
                || currentUser.hasCompanyPermission(companyId, Permissions.APPOINT_MANAGERS);
        if (!canAppoint) {
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

        H2 title = new H2("Role Management - " + companyId);
        title.getStyle().set("margin", "0").set("color", "white").set("flex", "1");

        HorizontalLayout actions = new HorizontalLayout();
        if (currentUser.hasCompanyPermission(companyId, Permissions.APPOINT_OWNERS)) {
            Button appointOwner = new Button("Appoint Owner", e -> openAppointOwnerDialog());
            appointOwner.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            actions.add(appointOwner);
        }
        if (currentUser.hasCompanyPermission(companyId, Permissions.APPOINT_MANAGERS)) {
            Button appointManager = new Button("Appoint Manager", e -> openAppointManagerDialog());
            appointManager.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            actions.add(appointManager);
        }

        header.add(title, actions);
        add(header);

        Result<List<RoleDTO>> result = presenter.getRoles(companyId);
        if (result instanceof Result.Fail<List<RoleDTO>> fail) {
            add(new Paragraph("Failed to load roles: " + fail.message()));
            return;
        }

        List<RoleDTO> roles = ((Result.Ok<List<RoleDTO>>) result).value();
        if (roles.isEmpty()) {
            add(new Paragraph("No roles found."));
            return;
        }

        FlexLayout rolesList = new FlexLayout();
        rolesList.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        rolesList.getStyle().set("gap", "16px");
        for (RoleDTO role : roles) {
            rolesList.add(buildRoleCard(role));
        }
        add(rolesList);

        Button back = new Button("Back", e -> UI.getCurrent().navigate("company/" + companyId));
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        add(back);
    }

    private VerticalLayout buildRoleCard(RoleDTO role) {
        VerticalLayout card = new VerticalLayout();
        card.getStyle()
                .set("border", "1px solid #e0e0e0")
                .set("border-radius", "8px")
                .set("padding", "16px")
                .set("box-shadow", "0 2px 4px rgba(0,0,0,0.1)")
                .set("background", "white");
        card.setWidth("350px");

        card.add(new H3(role.memberId));
        card.add(new Paragraph(role.roleType));

        if ("Manager".equals(role.roleType) && role.permissions != null && !role.permissions.isEmpty()) {
            for (Permissions p : role.permissions) {
                Paragraph perm = new Paragraph(formatPermission(p));
                perm.getStyle().set("margin", "0");
                card.add(perm);
            }
        } else if (!"Manager".equals(role.roleType)) {
            card.add(new Paragraph("All Permissions"));
        }

        boolean iCanRemove = role.appointerId != null
                && role.appointerId.equals(currentUser.getEmail());

        if (iCanRemove) {
            HorizontalLayout buttons = new HorizontalLayout();
            if ("Manager".equals(role.roleType)) {
                Button editPerms = new Button("Edit Permissions", e -> openEditPermissionsDialog(role));
                editPerms.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
                buttons.add(editPerms);
            }
            Button remove = new Button("Remove", e -> confirmRemove(role));
            remove.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            buttons.add(remove);
            card.add(buttons);
        }

        return card;
    }

    private void openAppointOwnerDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Appoint Owner");

        TextField emailField = new TextField("Member Email");
        emailField.setWidthFull();

        Button confirm = new Button("Send Invitation", e -> {
            String email = emailField.getValue().trim();
            if (email.isEmpty()) return;
            Result<Void> result = presenter.appointOwner(companyId, email);
            if (result instanceof Result.Ok) {
                showSuccess("Invitation sent to " + email);
                dialog.close();
            } else {
                showError(((Result.Fail<Void>) result).message());
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancel = new Button("Cancel", e -> dialog.close());
        dialog.add(emailField);
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    private void openAppointManagerDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Appoint Manager");

        TextField emailField = new TextField("Member Email");
        emailField.setWidthFull();

        VerticalLayout permissionsLayout = new VerticalLayout();
        permissionsLayout.setSpacing(false);
        permissionsLayout.setPadding(false);
        Map<Permissions, Checkbox> checkboxMap = new LinkedHashMap<>();
        for (Permissions p : Permissions.values()) {
            Checkbox cb = new Checkbox(formatPermission(p));
            checkboxMap.put(p, cb);
            permissionsLayout.add(cb);
        }

        Button confirm = new Button("Send Invitation", e -> {
            String email = emailField.getValue().trim();
            Set<Permissions> selected = checkboxMap.entrySet().stream()
                    .filter(en -> en.getValue().getValue())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            if (email.isEmpty()) return;
            Result<Void> result = presenter.appointManager(companyId, email, selected);
            if (result instanceof Result.Ok) {
                showSuccess("Invitation sent to " + email);
                dialog.close();
            } else {
                showError(((Result.Fail<Void>) result).message());
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancel = new Button("Cancel", e -> dialog.close());
        dialog.add(emailField, permissionsLayout);
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    private void confirmRemove(RoleDTO role) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Remove " + role.memberId + "?");
        dialog.add(new Paragraph("This will remove their role from the company."));

        Button confirm = new Button("Remove", e -> {
            Result<Void> result = presenter.removeAppointment(companyId, role.memberId);
            if (result instanceof Result.Ok) {
                showSuccess("Removed " + role.memberId);
                dialog.close();
                loadPage();
            } else {
                showError(((Result.Fail<Void>) result).message());
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_ERROR);

        Button cancel = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    private void openEditPermissionsDialog(RoleDTO role) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Permissions - " + role.memberId);

        VerticalLayout permissionsLayout = new VerticalLayout();
        permissionsLayout.setSpacing(false);
        permissionsLayout.setPadding(false);
        Map<Permissions, Checkbox> checkboxMap = new LinkedHashMap<>();
        for (Permissions p : Permissions.values()) {
            Checkbox cb = new Checkbox(formatPermission(p));
            cb.setValue(role.permissions.contains(p));
            checkboxMap.put(p, cb);
            permissionsLayout.add(cb);
        }

        Button save = new Button("Save", e -> {
            Set<Permissions> newPermissions = checkboxMap.entrySet().stream()
                    .filter(en -> en.getValue().getValue())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            Set<Permissions> toAdd = new HashSet<>(newPermissions);
            toAdd.removeAll(role.permissions);

            Set<Permissions> toRemove = new HashSet<>(role.permissions);
            toRemove.removeAll(newPermissions);

            if (!toAdd.isEmpty())
                presenter.addManagerPermissions(companyId, role.memberId, toAdd);
            if (!toRemove.isEmpty())
                presenter.removeManagerPermissions(companyId, role.memberId, toRemove);

            showSuccess("Permissions updated");
            dialog.close();
            loadPage();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancel = new Button("Cancel", e -> dialog.close());
        dialog.add(permissionsLayout);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void showSuccess(String msg) {
        Notification n = Notification.show(msg);
        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String msg) {
        Notification n = Notification.show(msg);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private String formatPermission(Permissions p) {
        return Arrays.stream(p.name().split("_"))
                .map(w -> w.charAt(0) + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

}