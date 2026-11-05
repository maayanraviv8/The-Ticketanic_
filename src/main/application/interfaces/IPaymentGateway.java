package application.interfaces;

import application.dto.PaymentDetailsDTO;

public interface IPaymentGateway {
    /**
     * Charges the customer for a reservation.
     *
     * @param paymentDetails the card / payer details collected at checkout
     * @return the transaction id of the successful charge — used as the order id so refunds
     *         can target it later — or {@code null} if the payment was declined.
     */
    String processPayment(String activeOrderId, double amount, PaymentDetailsDTO paymentDetails);

    /**
     * Refunds a previously successful charge.
     *
     * @param transactionId the transaction id returned by {@link #processPayment}
     *                       (which is also the order id)
     */
    void refundPayment(String transactionId, double amount);
}
