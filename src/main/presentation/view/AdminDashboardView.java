package presentation.view;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import presentation.CurrentUser;

@Route(value = "admin", layout = MainLayout.class)
@PageTitle("Admin Dashboard")
public class AdminDashboardView extends VerticalLayout implements BeforeEnterObserver {

    private final CurrentUser currentUser;

    public AdminDashboardView(CurrentUser currentUser) {
        this.currentUser = currentUser;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!currentUser.isLoggedIn() || !currentUser.isAdmin()) {
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
        H2 title = new H2("Admin Dashboard");
        title.getStyle().set("margin", "0").set("color", "white").set("flex", "1");
        header.add(title);
        add(header);

        FlexLayout cards = new FlexLayout();
        cards.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        cards.getStyle().set("gap", "24px").set("padding", "2rem");

        cards.add(buildCard("Production Company Management", "admin/companies"));
        cards.add(buildCard("Member Management", "admin/members"));
        cards.add(buildCard("Purchase History", "admin/history"));

        add(cards);
    }

    private VerticalLayout buildCard(String title, String route) {
        VerticalLayout card = new VerticalLayout();
        card.getStyle()
                .set("border", "1px solid #e0e0e0")
                .set("border-radius", "12px")
                .set("padding", "32px")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.1)")
                .set("background", "white")
                .set("cursor", "pointer")
                .set("width", "300px");

        H2 cardTitle = new H2(title);
        cardTitle.getStyle().set("font-size", "1.2rem").set("margin", "0");

        card.add(cardTitle);
        card.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate(route)));
        return card;
    }
}