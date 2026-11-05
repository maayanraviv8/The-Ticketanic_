package unit.infra.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import application.dto.PaymentDetailsDTO;
import infra.external.WsepExternalClient;
import infra.external.WsepExternalClient.PaymentRequest;
import infra.payment.HttpPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class HttpPaymentGatewayTest {

    private static final PaymentDetailsDTO CARD = new PaymentDetailsDTO(
            "2222333344445555", "4", "2030", "Ticketanic", "262", "USD", "20444444");

    private WsepExternalClient client;
    private HttpPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(WsepExternalClient.class);
        gateway = new HttpPaymentGateway(client);
    }

    @Test
    void approvedPaymentReturnsTransactionIdUsableForRefund() {
        when(client.pay(any(PaymentRequest.class))).thenReturn(54321);
        when(client.refund(54321)).thenReturn(true);

        // The returned transaction id is what the caller records as the order id.
        assertEquals("54321", gateway.processPayment("ao-1", 1000.0, CARD));

        // Refunding by that same id targets the external transaction — no internal bookkeeping.
        gateway.refundPayment("54321", 1000.0);
        verify(client).refund(54321);
    }

    @Test
    void cardDetailsFromDtoAreForwardedToPay() {
        when(client.pay(any(PaymentRequest.class))).thenReturn(54321);

        gateway.processPayment("ao-1", 1000.0, CARD);

        ArgumentCaptor<PaymentRequest> captor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(client).pay(captor.capture());
        PaymentRequest sent = captor.getValue();
        assertEquals("1000", sent.amount());
        assertEquals(CARD.getCurrency(), sent.currency());
        assertEquals(CARD.getCardNumber(), sent.cardNumber());
        assertEquals(CARD.getMonth(), sent.month());
        assertEquals(CARD.getYear(), sent.year());
        assertEquals(CARD.getHolder(), sent.holder());
        assertEquals(CARD.getCvv(), sent.cvv());
        assertEquals(CARD.getPayerId(), sent.payerId());
    }

    @Test
    void declinedPaymentReturnsNull() {
        when(client.pay(any(PaymentRequest.class))).thenReturn(WsepExternalClient.FAILURE_CODE);

        assertNull(gateway.processPayment("ao-2", 50.0, CARD));
        verify(client, never()).refund(Mockito.anyInt());
    }

    @Test
    void refundWithNonNumericTransactionIdIsNoOp() {
        gateway.refundPayment("not-a-transaction", 10.0);
        verify(client, never()).refund(Mockito.anyInt());
    }
}
