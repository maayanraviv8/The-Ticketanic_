package infra.payment;

import application.dto.PaymentDetailsDTO;
import application.interfaces.IPaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development stub for {@link IPaymentGateway}.
 * Always reports successful payment. Used when the real external payment system is
 * disabled ({@code ticketanic.external.payment.enabled=false}, the default in dev/test);
 * {@link HttpPaymentGateway} takes over when it is enabled. Exactly one bean exists.
 */
@Component
@ConditionalOnProperty(prefix = "ticketanic.external.payment", name = "enabled",
        havingValue = "false", matchIfMissing = true)
public class StubPaymentGateway implements IPaymentGateway {

    private static final Logger logger = LoggerFactory.getLogger(StubPaymentGateway.class);

    @Override
    public String processPayment(String activeOrderId, double amount, PaymentDetailsDTO paymentDetails) {
        logger.info("StubPaymentGateway.processPayment activeOrderId={} amount={}",
                activeOrderId, amount);
        // No real transaction; echo the active-order id so the order id stays stable in dev/test.
        return activeOrderId;
    }

    @Override
    public void refundPayment(String transactionId, double amount) {
        logger.info("StubPaymentGateway.refundPayment transactionId={} amount={}", transactionId, amount);
    }
}
