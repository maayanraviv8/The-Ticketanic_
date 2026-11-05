package presentation.view;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "suspended", layout = MainLayout.class)
@PageTitle("Account suspended")
public class SuspendedAccountView extends VerticalLayout {

    public SuspendedAccountView() {
        add(new H2("Your account is suspended"));
        add(new Paragraph(
                "While your account is suspended you can only view information; " +
                "you cannot purchase tickets, manage roles, edit policies, or perform other actions."));
        add(new Paragraph(
                "You may still browse events, view your past orders, and read notifications."));
        setPadding(true);
        setSpacing(true);
    }
}
