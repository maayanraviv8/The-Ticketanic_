package application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Card / payer details collected at checkout and passed to the payment gateway.
 *
 * <p>Pure data container — no business logic. Fields are kept as {@code String} because
 * they map verbatim onto the external {@code pay} action's form parameters. This DTO is
 * transient: it is never persisted on the {@code Order} aggregate.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDetailsDTO {
    private String cardNumber;
    private String month;
    private String year;
    private String holder;
    private String cvv;
    private String currency;
    private String payerId;
}
