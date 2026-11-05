package unit.presentation;

import application.dto.EventDTO;
import application.dto.RaffleStatusDTO;
import application.dto.ShowDTO;
import application.dto.VenueMapDTO;
import application.service.EventService;
import application.service.ShowService;
import domain.exceptions.EventNotFoundException;
import domain.exceptions.RaffleException;
import domain.exceptions.VenueMapNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import presentation.CurrentUser;
import presentation.presenter.EventDetailPresenter;
import presentation.Result;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventDetailPresenterTest {

    private EventService eventService;
    private ShowService showService;
    private CurrentUser currentUser;
    private EventDetailPresenter presenter;

    @BeforeEach
    void setUp() {
        eventService = mock(EventService.class);
        showService = mock(ShowService.class);
        currentUser = mock(CurrentUser.class);
        when(currentUser.getToken()).thenReturn("token");
        presenter = new EventDetailPresenter(eventService, showService, currentUser);
    }

    @Test
    void givenExistingEvent_whenGetEvent_thenReturnsOk() {
        EventDTO dto = new EventDTO();
        dto.eventId = "e1";
        when(eventService.getEventById("e1")).thenReturn(dto);
        Result<EventDTO> result = presenter.getEvent("e1");
        assertInstanceOf(Result.Ok.class, result);
        assertEquals("e1", ((Result.Ok<EventDTO>) result).value().eventId);
    }

    @Test
    void givenMissingEvent_whenGetEvent_thenReturnsFail() {
        when(eventService.getEventById("bad")).thenThrow(new EventNotFoundException("bad"));
        Result<EventDTO> result = presenter.getEvent("bad");
        assertInstanceOf(Result.Fail.class, result);
    }

    @Test
    void givenShows_whenGetShowsForEvent_thenReturnsOk() {
        when(showService.getShowsByEvent("e1")).thenReturn(List.of(new ShowDTO()));
        Result<List<ShowDTO>> result = presenter.getShowsForEvent("e1");
        assertInstanceOf(Result.Ok.class, result);
        assertEquals(1, ((Result.Ok<List<ShowDTO>>) result).value().size());
    }

    @Test
    void givenVenueMap_whenGetShowMap_thenReturnsOk() {
        VenueMapDTO dto = new VenueMapDTO();
        when(showService.getHallLayout("s1")).thenReturn(dto);
        Result<VenueMapDTO> result = presenter.getShowMap("s1");
        assertInstanceOf(Result.Ok.class, result);
    }

    @Test
    void givenNoVenueMap_whenGetShowMap_thenReturnsFail() {
        when(showService.getHallLayout("s1")).thenThrow(new VenueMapNotFoundException("s1"));
        Result<VenueMapDTO> result = presenter.getShowMap("s1");
        assertInstanceOf(Result.Fail.class, result);
        assertEquals("No venue map defined for this show.", ((Result.Fail<VenueMapDTO>) result).message());
    }

    @Test
    void givenStatus_whenGetRaffleStatus_thenReturnsOk() {
        RaffleStatusDTO dto = new RaffleStatusDTO();
        when(eventService.getRaffleStatus("e1", "token")).thenReturn(dto);
        Result<RaffleStatusDTO> result = presenter.getRaffleStatus("e1");
        assertInstanceOf(Result.Ok.class, result);
    }

    @Test
    void givenSuccess_whenRegisterForRaffle_thenReturnsOk() {
        Result<Void> result = presenter.registerForRaffle("e1");
        assertInstanceOf(Result.Ok.class, result);
        verify(eventService).registerForRaffle("e1", "token");
    }

    @Test
    void givenRaffleException_whenRegisterForRaffle_thenReturnsFailWithMessage() {
        doThrow(new RaffleException("Raffle registration has closed."))
                .when(eventService).registerForRaffle("e1", "token");
        Result<Void> result = presenter.registerForRaffle("e1");
        assertInstanceOf(Result.Fail.class, result);
        assertEquals("Raffle registration has closed.", ((Result.Fail<Void>) result).message());
    }
}
