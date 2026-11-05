package presentation;

import com.vaadin.flow.component.html.Image;

/**
 * Single source of truth for the Ticketanic brand logo. The logo image already
 * contains the "Ticketanic" wordmark, so views that show it should not also
 * render the brand name as text.
 * <p>
 * Served from {@code src/main/resources/META-INF/resources/images/logo.png}.
 */
public final class Brand {

    public static final String LOGO_SRC = "images/logo.png";

    private Brand() {
    }

    /**
     * A logo {@link Image} sized to the given CSS height (width auto), with the
     * logo's square corners softened to match the Lumo card vocabulary.
     */
    public static Image logo(String height) {
        Image image = new Image(LOGO_SRC, "Ticketanic");
        image.setHeight(height);
        image.getStyle()
                .set("width", "auto")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("display", "block");
        return image;
    }
}
