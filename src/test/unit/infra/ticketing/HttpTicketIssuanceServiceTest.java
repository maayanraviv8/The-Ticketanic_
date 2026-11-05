package unit.infra.ticketing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import application.dto.TicketIssuanceRequestDTO;
import application.dto.TicketIssuanceRequestDTO.SeatRef;
import infra.config.ExternalServicesProperties;
import infra.external.WsepExternalClient;
import infra.ticketing.HttpTicketIssuanceService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class HttpTicketIssuanceServiceTest {

    private WsepExternalClient client;
    private HttpTicketIssuanceService service;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(WsepExternalClient.class);
        ExternalServicesProperties props = new ExternalServicesProperties(
                "http://localhost/", 1000, 5000, null, null, null, null);
        service = new HttpTicketIssuanceService(client, props);
    }

    @Test
    void standingOrderIssuesOneCallWithQuantityAndReturnsCode() {
        when(client.issueStandingTicket(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("TIX-aaaa-1111");

        TicketIssuanceRequestDTO request = new TicketIssuanceRequestDTO(
                "cust@mail.com", "EVT-1", "Golden Ring", true, 3, List.of());

        assertEquals("TIX-aaaa-1111", service.issueTickets("ao-1", request));

        verify(client).issueStandingTicket("cust@mail.com", "EVT-1", "Golden Ring", 3);
        verify(client, never()).issueSeatedTickets(anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void seatedOrderIssuesOneCallWithRealSeatsAndReturnsCode() {
        when(client.issueSeatedTickets(anyString(), anyString(), anyString(), anyList()))
                .thenReturn("TIX-bbbb-2222");

        TicketIssuanceRequestDTO request = new TicketIssuanceRequestDTO(
                "cust@mail.com", "EVT-1", "VIP Balcony", false, 2,
                List.of(new SeatRef(4, 12), new SeatRef(4, 13)));

        assertEquals("TIX-bbbb-2222", service.issueTickets("ao-2", request));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WsepExternalClient.SeatRef>> seatsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(client).issueSeatedTickets(eq("cust@mail.com"), eq("EVT-1"), eq("VIP Balcony"),
                seatsCaptor.capture());
        verify(client, never()).issueStandingTicket(anyString(), anyString(), anyString(), anyInt());

        List<WsepExternalClient.SeatRef> seats = seatsCaptor.getValue();
        assertTrue(seats.contains(new WsepExternalClient.SeatRef(4, 12)));
        assertTrue(seats.contains(new WsepExternalClient.SeatRef(4, 13)));
    }

    @Test
    void returnsNullWhenExternalSystemReturnsNoCode() {
        when(client.issueStandingTicket(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(null);

        TicketIssuanceRequestDTO request = new TicketIssuanceRequestDTO(
                "cust@mail.com", "EVT-1", "Golden Ring", true, 1, List.of());

        assertNull(service.issueTickets("ao-3", request));
    }

    @Test
    void blankZoneNameFallsBackToDefaultZone() {
        when(client.issueStandingTicket(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("TIX-cccc-3333");

        TicketIssuanceRequestDTO request = new TicketIssuanceRequestDTO(
                "cust@mail.com", "EVT-1", null, true, 1, List.of());

        assertEquals("TIX-cccc-3333", service.issueTickets("ao-4", request));

        // Default zone from ExternalServicesProperties.TicketIssuance is "General Admission".
        verify(client).issueStandingTicket("cust@mail.com", "EVT-1", "General Admission", 1);
    }

    @Test
    void nullRequestReturnsNullWithoutCallingExternalSystem() {
        assertNull(service.issueTickets("ao-5", null));
        verify(client, never()).issueStandingTicket(anyString(), anyString(), anyString(), anyInt());
        verify(client, never()).issueSeatedTickets(anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void cancelIssuedTicketsDelegatesToClient() {
        when(client.cancelTicket("TIX-aaaa-1111")).thenReturn(true);
        assertTrue(service.cancelIssuedTickets("TIX-aaaa-1111"));
        verify(client).cancelTicket("TIX-aaaa-1111");
    }
}
