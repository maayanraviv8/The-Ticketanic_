package infra.payment;

import application.dto.PaymentDetailsDTO;
import application.interfaces.IPaymentGateway;
import infra.external.WsepExternalClient;
import infra.external.WsepExternalClient.PaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Real {@link IPaymentGateway} backed by the external WSEP payment system (issue #283).
 *
 * <p>Active only when {@code ticketanic.external.payment.enabled=true}; otherwise
 * {@link StubPaymentGateway} is used. Exactly one of the two beans exists.
 *
 * <p>The {@code refund} action requires the {@code transaction_id} returned by a prior
 * {@code pay}. {@link #processPayment} returns that id, which the caller records as the order id;
 * {@link #refundPayment} then receives it back and forwards it to the external system. The adapter
 * itself is therefore stateless — no {@code activeOrderId -> transactionId} bookkeeping is needed.
 *
 * <p>The card / payer details forwarded to {@code pay} come from the {@link PaymentDetailsDTO}
 * collected at checkout (issue #334).
 */
@Component
@ConditionalOnProperty(prefix = "ticketanic.external.payment", name = "enabled", havingValue = "true")
public class HttpPaymentGateway implements IPaymentGateway {

    private static final Logger logger = LoggerFactory.getLogger(HttpPaymentGateway.class);

    private final WsepExternalClient client;

    public HttpPaymentGateway(WsepExternalClient client) {
        this.client = client;
    }

    @Override
    public String processPayment(String activeOrderId, double amount, PaymentDetailsDTO paymentDetails) {
        logger.info("HttpPaymentGateway.processPayment activeOrderId={} amount={} currency={}",
                activeOrderId, amount, paymentDetails.getCurrency());

        PaymentRequest request = new PaymentRequest(
                formatAmount(amount),
                paymentDetails.getCurrency(),
                paymentDetails.getCardNumber(),
                paymentDetails.getMonth(),
                paymentDetails.getYear(),
                paymentDetails.getHolder(),
                paymentDetails.getCvv(),
                paymentDetails.getPayerId());

        int transactionId = client.pay(request);
        if (transactionId < 0) {
            logger.warn("Payment declined by external system for activeOrderId={}", activeOrderId);
            return null;
        }

        logger.info("Payment approved for activeOrderId={} transactionId={}", activeOrderId, transactionId);
        return Integer.toString(transactionId);
    }

    @Override
    public void refundPayment(String transactionId, double amount) {
        int externalTransactionId;
        try {
            externalTransactionId = Integer.parseInt(transactionId);
        } catch (NumberFormatException e) {
            logger.warn("Refund requested with non-numeric transactionId='{}'; nothing to refund", transactionId);
            return;
        }

        logger.info("HttpPaymentGateway.refundPayment transactionId={} amount={}", transactionId, amount);
        boolean refunded = client.refund(externalTransactionId);
        if (refunded) {
            logger.info("Refund succeeded for transactionId={}", transactionId);
        } else {
            logger.warn("Refund failed at external system for transactionId={}", transactionId);
        }
    }

    /** External {@code pay} expects a plain decimal amount with no grouping separators. */
    private static String formatAmount(double amount) {
        if (amount == Math.rint(amount)) {
            return Long.toString((long) amount);
        }
        return Double.toString(amount);
    }
}
