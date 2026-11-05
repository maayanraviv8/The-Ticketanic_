package presentation.view;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "access-denied", layout = MainLayout.class)
@PageTitle("Access denied")
public class AccessDeniedView extends VerticalLayout {

    public AccessDeniedView() {
        add(new H2("Access denied"));
        add(new Paragraph("You don't have permission to view that page."));
        add(new Paragraph("Use the menu to navigate to a page you can access."));
        setPadding(true);
        setSpacing(true);
    }
}
