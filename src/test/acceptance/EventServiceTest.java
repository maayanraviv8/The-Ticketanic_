package acceptance;

import application.dto.EventDTO;
import application.dto.OrderDTO;
import application.dto.RaffleStatusDTO;
import application.service.EventService;
import domain.Aggregates.event.Event;
import domain.Aggregates.event.RaffleStatus;
import domain.Aggregates.productioncompany.Permissions;
import domain.Aggregates.productioncompany.ProductionCompany;
import domain.Aggregates.show.Show;
import domain.exceptions.EventNotFoundException;
import domain.exceptions.InvalidEventException;
import domain.exceptions.RaffleException;
import domain.exceptions.UnauthorizedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class EventServiceTest extends AcceptanceTestBase {

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepo, authService, companyRepo, notificationService, orderRepo, showRepo, discountPolicyFactory, purchasePolicyFactory, pubEvent -> {});
    }

    ///////// UC3 - Event Search /////////
    
    @Test
    void givenValidEventKeyword_whenSearchEvents_thenReturnsEventDTO() {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true); // Artist name is VALID_ARTIST_NAME, event name is VALID_EVENT_ID
        
        // Act
        List<EventDTO> results1 = assertDoesNotThrow(() -> eventService.searchEvents(null, List.of(VALID_ARTIST_NAME), null, null, null, null, null, null)); // Search for artist name
        List<EventDTO> results2 = assertDoesNotThrow(() -> eventService.searchEvents(null, List.of(VALID_EVENT_ID), null, null, null, null, null, null)); // Search for event name

        // Assert
        assertNotNull(results1);
        assertEquals(1, results1.size());
        assertEquals(event.getEventId(), results1.get(0).eventId);
        assertEquals(VALID_ARTIST_NAME, results1.get(0).artist);
        assertNotNull(results2);
        assertEquals(1, results2.size());
        assertEquals(event.getEventId(), results2.get(0).eventId);
        assertEquals(VALID_EVENT_ID, results2.get(0).name);
    }

    @Test
    void givenValidCategory_whenSearchEvents_thenReturnsEventDTO() {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true); // Event type is VALID_EVENT_TYPE_NAME
        
        // Act
        List<EventDTO> results = assertDoesNotThrow(() -> eventService.searchEvents(null, null, VALID_EVENT_TYPE_NAME, null, null, null, null, null)); // Search for event type

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(event.getEventId(), results.get(0).eventId);
        assertEquals(VALID_EVENT_TYPE_NAME, results.get(0).eventTypeName);
    }

    @Test
    void givenValidDateRange_whenSearchEvents_thenReturnsEventDTO() {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Act — search with a date range that contains the event's dates
        List<EventDTO> results = assertDoesNotThrow(() ->
                eventService.searchEvents(null, null, null, VALID_START_DATE.minusDays(1), VALID_END_DATE.plusDays(1), null, null, null));

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(event.getEventId(), results.get(0).eventId);
    }

    @Test
    void givenValidLocation_whenSearchEvents_thenReturnsEventDTO() {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Act — search by the event's location
        List<EventDTO> results = assertDoesNotThrow(() ->
                eventService.searchEvents(null, null, null, null, null, VALID_LOCATION, null, null));

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(event.getEventId(), results.get(0).eventId);
        assertEquals(VALID_LOCATION, results.get(0).location);
    }

    @Test
    void givenValidPriceRange_whenSearchEvents_thenReturnsEventDTO() {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Act — search with a price range that contains the event's min/max price
        List<EventDTO> results = assertDoesNotThrow(() ->
                eventService.searchEvents(null, null, null, null, null, null, VALID_MIN_PRICE - 10, VALID_MAX_PRICE + 10));

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(event.getEventId(), results.get(0).eventId);
    }

    @Test
    void givenNonMatchingKeyword_whenSearchEvents_thenReturnsEmptyList() {
        // Arrange — event exists but keyword doesn't match
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeEvent(VALID_COMPANY_NAME, true);

        // Act
        List<EventDTO> results = assertDoesNotThrow(() ->
                eventService.searchEvents(null, List.of(NON_MATCHING_KEYWORD), null, null, null, null, null, null));

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void givenNonMatchingCategory_whenSearchEvents_thenReturnsEmptyList() {
        // Arrange — event exists but category doesn't match
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeEvent(VALID_COMPANY_NAME, true);

        // Act
        List<EventDTO> results = assertDoesNotThrow(() ->
                eventService.searchEvents(null, null, NON_MATCHING_CATEGORY, null, null, null, null, null));

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void givenNonMatchingDateRange_whenSearchEvents_thenReturnsEmptyList() {
        // Arrange — event exists but its dates are outside the searched range
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeEvent(VALID_COMPANY_NAME, true); // event: VALID_START_DATE to VALID_END_DATE

        // Act — search for a range that is entirely in the future
        List<EventDTO> results = assertDoesNotThrow(() ->
                eventService.searchEvents(null, null, null, VALID_END_DATE.plusDays(1), VALID_END_DATE.plusDays(10), null, null, null));

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void givenNonMatchingLocation_whenSearchEvents_thenReturnsEmptyList() {
        // Arrange — event exists but location doesn't match
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeEvent(VALID_COMPANY_NAME, true);

        // Act
        List<EventDTO> results = assertDoesNotThrow(() ->
                eventService.searchEvents(null, null, null, null, null, NON_MATCHING_LOCATION, null, null));

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void givenNonMatchingPriceRange_whenSearchEvents_thenReturnsEmptyList() {
        // Arrange — event exists but its price is outside the searched range
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeEvent(VALID_COMPANY_NAME, true); // event: VALID_MIN_PRICE to VALID_MAX_PRICE

        // Act — search for a price range entirely below the event's price
        List<EventDTO> results = assertDoesNotThrow(() ->
                eventService.searchEvents(null, null, null, null, null, null, NON_MATCHING_MIN_PRICE, VALID_MIN_PRICE - 1));

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void givenValidProductionCompany_whenSearchEvents_thenReturnsEventDTO() {
        // Arrange
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true); // Event is under VALID_COMPANY_NAME

        // Act
        List<EventDTO> results = assertDoesNotThrow(() -> eventService.searchEvents(VALID_COMPANY_NAME, null, null, null, null, null, null, null));

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(event.getEventId(), results.get(0).eventId);
        assertEquals(event.getProductionCompanyName(), results.get(0).productionCompanyName);
    }

    @Test
    void givenProductionCompanyWithNoEvents_whenSearchEventsByCompany_thenReturnsEmptyList() {
        // Arrange — company exists but has no events; event belongs to a different company
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeCompany(VALID_COMPANY_NAME_2, VALID_EMAIL);
        storeEvent(VALID_COMPANY_NAME_2, true); // event is under company 2, not company 1

        // Act — search under company 1
        List<EventDTO> results = assertDoesNotThrow(() ->
                eventService.searchEvents(VALID_COMPANY_NAME, null, null, null, null, null, null, null));

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void givenNonExistentEvent_whenSearchEvents_thenReturnsEmptyList() {
        // Arrange — nothing exists

        // Act
        List<EventDTO> results = assertDoesNotThrow(() ->
                eventService.searchEvents(null, List.of(VALID_EVENT_ID), null, null, null, null, null, null));

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    ///////// UC14 - Manage Event - create event, edit event, remove event /////////

    /// create event
    @Test
    void givenValidEventDetailsByValidManager_whenCreateEvent_thenReturnsEventDTO() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        EventDTO eventDTO = createValidEventDTO();

        // Act
        EventDTO result = assertDoesNotThrow(() -> eventService.createEvent(eventDTO, company.getName(), token));

        // Assert
        assertNotNull(result);
        assertEquals(eventDTO.name, result.name);
        assertEquals(eventDTO.eventTypeName, result.eventTypeName);
        assertEquals(eventDTO.startDate, result.startDate);

        //Assert - internal state
        Event event = eventRepo.findById(result.eventId);
        assertNotNull(event);
        assertEquals(eventDTO.name, event.getName());
        assertEquals(VALID_COMPANY_NAME, event.getProductionCompanyName());
        assertTrue(event.isActive());
        assertNull(event.getPurchasePolicy());
        assertNull(event.getDiscountPolicy());
        assertNull(event.getRaffle());
    }

    @Test
    void givenValidEventDetailsByFounder_whenCreateEvent_thenReturnsEventDTO() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);

        EventDTO eventDTO = createValidEventDTO();

        // Act
        EventDTO result = assertDoesNotThrow(() -> eventService.createEvent(eventDTO, VALID_COMPANY_NAME, token));

        // Assert - return value
        assertNotNull(result);
        assertEquals(eventDTO.name, result.name);
        assertEquals(eventDTO.eventTypeName, result.eventTypeName);
        assertEquals(eventDTO.startDate, result.startDate);

        // Assert - internal state
        Event event = eventRepo.findById(result.eventId);
        assertNotNull(event);
        assertEquals(eventDTO.name, event.getName());
        assertEquals(VALID_COMPANY_NAME, event.getProductionCompanyName());
        assertTrue(event.isActive());
        assertNull(event.getPurchasePolicy());
        assertNull(event.getDiscountPolicy());
        assertNull(event.getRaffle());
    }

    @Test
    void givenInvalidManagerPermissions_whenCreateEvent_thenThrowsExceptionEventNotCreated() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        // Invalid manager permissions - default permissions
        storeManagerInCompany(company.getName(), company.getFounderID(), VALID_EMAIL);

        EventDTO eventDTO = createValidEventDTO();

        // Act
        assertThrows(Exception.class, () -> eventService.createEvent(eventDTO, company.getName(), token));

        //Assert - internal state
        assertEquals(0, eventRepo.getAll().size());
    }

    @Test
    void givenNullEventName_whenCreateEvent_thenThrowsAndEventNotCreated() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);
        EventDTO eventDTO = createValidEventDTO();
        eventDTO.name = null;

        // Act & Assert
        assertThrows(Exception.class, () -> eventService.createEvent(eventDTO, company.getName(), token));

        // Assert - internal state
        assertEquals(0, eventRepo.getAll().size());
    }

    @Test
    void givenNullStartDate_whenCreateEvent_thenThrowsAndEventNotCreated() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);
        EventDTO eventDTO = createValidEventDTO();
        eventDTO.startDate = null;

        // Act & Assert
        assertThrows(Exception.class, () -> eventService.createEvent(eventDTO, company.getName(), token));

        // Assert - internal state
        assertEquals(0, eventRepo.getAll().size());
    }

    @Test
    void givenNullEndDate_whenCreateEvent_thenThrowsAndEventNotCreated() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);
        EventDTO eventDTO = createValidEventDTO();
        eventDTO.endDate = null;

        // Act & Assert
        assertThrows(Exception.class, () -> eventService.createEvent(eventDTO, company.getName(), token));

        // Assert - internal state
        assertEquals(0, eventRepo.getAll().size());
    }

    @Test
    void givenEndDateBeforeStartDate_whenCreateEvent_thenThrowsAndEventNotCreated() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);
        EventDTO eventDTO = createValidEventDTO();
        eventDTO.startDate = VALID_END_DATE;
        eventDTO.endDate   = VALID_START_DATE;

        // Act & Assert
        assertThrows(Exception.class, () -> eventService.createEvent(eventDTO, company.getName(), token));

        // Assert - internal state
        assertEquals(0, eventRepo.getAll().size());
    }

    /// edit event

    @Test
    void givenValidEventDetailsByValidManager_whenEditEvent_thenEventUpdated() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        EventDTO updatedDTO = createValidEventDTO();
        updatedDTO.name = VALID_EVENT_ID_2;

        // Act
        EventDTO result = assertDoesNotThrow(() -> eventService.editEvent(event.getEventId(), updatedDTO, token));

        // Assert - return value
        assertNotNull(result);
        assertEquals(VALID_EVENT_ID_2, result.name);

        // Assert - internal state
        assertEquals(VALID_EVENT_ID_2, eventRepo.findById(event.getEventId()).getName());
        assertEquals(company.getName(), eventRepo.findById(event.getEventId()).getProductionCompanyName());
    }

    @Test
    void givenValidEventDetailsByFounder_whenEditEvent_thenEventUpdated() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        EventDTO updatedDTO = createValidEventDTO();
        updatedDTO.name = VALID_EVENT_ID_2;

        // Act
        EventDTO result = assertDoesNotThrow(() -> eventService.editEvent(event.getEventId(), updatedDTO, token));

        // Assert - return value
        assertNotNull(result);
        assertEquals(VALID_EVENT_ID_2, result.name);

        // Assert - internal state
        assertEquals(VALID_EVENT_ID_2, eventRepo.findById(event.getEventId()).getName());
        assertEquals(VALID_COMPANY_NAME, eventRepo.findById(event.getEventId()).getProductionCompanyName());
    }

    @Test
    void givenInvalidManagerPermissions_whenEditEvent_thenThrowsAndEventUnchanged() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompany(company.getName(), company.getFounderID(), VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        EventDTO updatedDTO = createValidEventDTO();
        updatedDTO.name = VALID_EVENT_ID_2;

        // Act & Assert
        assertThrows(Exception.class, () -> eventService.editEvent(event.getEventId(), updatedDTO, token));

        // Assert - internal state unchanged
        assertEquals(VALID_EVENT_ID, eventRepo.findById(event.getEventId()).getName());
    }

    @Test
    void givenNullEventName_whenEditEvent_thenThrowsAndEventUnchanged() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        String originalName = event.getName();

        EventDTO updatedDTO = createValidEventDTO();
        updatedDTO.name = null;

        // Act & Assert
        assertThrows(Exception.class, () -> eventService.editEvent(event.getEventId(), updatedDTO, token));

        // Assert - internal state unchanged
        assertEquals(originalName, eventRepo.findById(event.getEventId()).getName());
    }

    @Test
    void givenNullStartDate_whenEditEvent_thenThrowsAndEventUnchanged() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        LocalDateTime originalStart = event.getStartDate();

        EventDTO updatedDTO = createValidEventDTO();
        updatedDTO.startDate = null;

        // Act & Assert
        assertThrows(Exception.class, () -> eventService.editEvent(event.getEventId(), updatedDTO, token));

        // Assert - internal state unchanged
        assertEquals(originalStart, eventRepo.findById(event.getEventId()).getStartDate());
    }

    @Test
    void givenNullEndDate_whenEditEvent_thenThrowsAndEventUnchanged() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        LocalDateTime originalEnd = event.getEndDate();

        EventDTO updatedDTO = createValidEventDTO();
        updatedDTO.endDate = null;

        // Act & Assert
        assertThrows(Exception.class, () -> eventService.editEvent(event.getEventId(), updatedDTO, token));

        // Assert - internal state unchanged
        assertEquals(originalEnd, eventRepo.findById(event.getEventId()).getEndDate());
    }

    @Test
    void givenEndDateBeforeStartDate_whenEditEvent_thenThrowsAndEventUnchanged() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        LocalDateTime originalStart = event.getStartDate();
        LocalDateTime originalEnd = event.getEndDate();

        EventDTO updatedDTO = createValidEventDTO();
        updatedDTO.startDate = VALID_END_DATE;
        updatedDTO.endDate   = VALID_START_DATE;

        // Act & Assert
        assertThrows(Exception.class, () -> eventService.editEvent(event.getEventId(), updatedDTO, token));

        // Assert - internal state unchanged
        assertEquals(originalStart, eventRepo.findById(event.getEventId()).getStartDate());
        assertEquals(originalEnd, eventRepo.findById(event.getEventId()).getEndDate());
    }

    @Test
    void givenNonExistentEvent_whenEditEvent_thenThrowsAndNoEventCreated() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        EventDTO updatedDTO = createValidEventDTO();

        // Act & Assert
        assertThrows(Exception.class, () -> eventService.editEvent("non-existent-id", updatedDTO, token));

        // Assert - internal state
        assertEquals(0, eventRepo.getAll().size());
    }

    /// remove event
    @Test
    void givenValidEventWithoutShowsByValidManager_whenRemoveEvent_thenEventRemoved() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Act
        assertDoesNotThrow(() -> eventService.removeEvent(event.getEventId(), token));

        // Assert - internal state
        assertNull(eventRepo.findById(event.getEventId()));
    }

    @Test
    void givenValidEventWithoutShowsByFounder_whenRemoveEvent_thenEventRemoved() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Act
        assertDoesNotThrow(() -> eventService.removeEvent(event.getEventId(), token));

        // Assert - internal state
        assertNull(eventRepo.findById(event.getEventId()));
    }

    @Test
    void givenInvalidManagerPermissions_whenRemoveEvent_thenThrowsAndEventUnchanged() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompany(company.getName(), company.getFounderID(), VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Act & Assert
        assertThrows(Exception.class, () -> eventService.removeEvent(event.getEventId(), token));

        // Assert - internal state unchanged
        Event newEvent = eventRepo.findById(event.getEventId());
        assertNotNull(newEvent);
        assertEquals(VALID_EVENT_ID, newEvent.getName());
        assertEquals(VALID_COMPANY_NAME, newEvent.getProductionCompanyName());
        assertTrue(newEvent.isActive());
    }

    @Test
    void givenNonExistentEvent_whenRemoveEvent_thenThrowsAndNoEventRemoved() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        //Act
        assertThrows(Exception.class, () -> eventService.removeEvent(VALID_EVENT_ID, token));

        // Assert - internal state
        assertNull(eventRepo.findById(VALID_EVENT_ID));
    }

    @Test
    void givenValidEventWithShowsByValidManager_whenRemoveEvent_thenEventRemoved() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true);

        // Act
        assertDoesNotThrow(() -> eventService.removeEvent(event.getEventId(), token));

        // Assert - internal state
        assertNull(eventRepo.findById(event.getEventId()));
        assertNull(showRepo.findById(show.getShowId()));
    }



    ///////// UC17 - View Purchase History /////////

    @Test
    void givenManagerWithViewPurchaseHistoryPermission_whenGetPurchaseHistoryByProductionCompany_thenReturnsOrders() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); //founder
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // member that have order
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL); 
        storeManagerInCompanyWithPermissions(VALID_COMPANY_NAME, VALID_EMAIL, MANAGER1_EMAIL,
                EnumSet.of(Permissions.VIEW_PURCHASE_HISTORY));
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1,
                event.getEventId(), VALID_SHOW_ID, VALID_TICKET_PRICE, LocalDateTime.now());
        String token = storeMemberAndLogin(MANAGER1_USERNAME, MANAGER1_EMAIL, VALID_PASSWORD);

        // Act
        List<OrderDTO> result = assertDoesNotThrow(() ->
                eventService.getPurchaseHistoryByProductionCompany(VALID_COMPANY_NAME, null, null, token));

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(ORDER_ID_1, result.get(0).getOrderId());
        assertEquals(VALID_TICKET_PRICE, result.get(0).getFinalPrice());
    }

    @Test
    void givenOwnerAndCompanyWithTwoOrders_whenGetPurchaseHistoryByProductionCompany_thenReturnsBothOrders() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); //founder
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // member that have order
        storeMember(VALID_USERNAME_3, VALID_EMAIL_3, VALID_PASSWORD); // member that have order
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL); 
        storeOwnerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, OWNER1_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1,
                event.getEventId(), VALID_SHOW_ID, VALID_TICKET_PRICE, LocalDateTime.now());
        storeFullPurchaseChain(VALID_EMAIL_3, ACTIVE_ORDER_ID_2, ORDER_ID_2,
                event.getEventId(), VALID_SHOW_ID, VALID_TICKET_PRICE, LocalDateTime.now());
        String token = storeMemberAndLogin(OWNER1_USERNAME, OWNER1_EMAIL, VALID_PASSWORD);

        // Act
        List<OrderDTO> result = assertDoesNotThrow(() ->
                eventService.getPurchaseHistoryByProductionCompany(VALID_COMPANY_NAME, null, null, token));

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(o -> o.getOrderId().equals(ORDER_ID_1)));
        assertTrue(result.stream().anyMatch(o -> o.getOrderId().equals(ORDER_ID_2)));
        assertEquals(VALID_TICKET_PRICE, result.get(0).getFinalPrice());
        assertEquals(VALID_TICKET_PRICE, result.get(1).getFinalPrice());
    }


    @Test
    void givenNonExistentCompany_whenGetPurchaseHistoryByProductionCompany_thenThrowsException() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); //founder
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // member that have order
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL); 
        storeManagerInCompanyWithPermissions(VALID_COMPANY_NAME, VALID_EMAIL, MANAGER1_EMAIL,
                EnumSet.of(Permissions.VIEW_PURCHASE_HISTORY));
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1,
                event.getEventId(), VALID_SHOW_ID, VALID_TICKET_PRICE, LocalDateTime.now());
        String token = storeMemberAndLogin(MANAGER1_USERNAME, MANAGER1_EMAIL, VALID_PASSWORD);

        // Act & Assert 
        assertThrows(Exception.class, () ->
                eventService.getPurchaseHistoryByProductionCompany(NON_EXISTENT_COMPANY_NAME, null, null, token));
    }

    @Test
    void givenManagerWithoutPermission_whenGetPurchaseHistoryByProductionCompany_thenThrowsException() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); //founder
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // member that have order
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL); 
        storeManagerInCompany(VALID_COMPANY_NAME, VALID_EMAIL, MANAGER1_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1,
                event.getEventId(), VALID_SHOW_ID, VALID_TICKET_PRICE, LocalDateTime.now());
        String token = storeMemberAndLogin(MANAGER1_USERNAME, MANAGER1_EMAIL, VALID_PASSWORD);

        // Act & Assert
        assertThrows(Exception.class, () ->
                eventService.getPurchaseHistoryByProductionCompany(VALID_COMPANY_NAME, null, null, token));
    }

    @Test
    void givenPostPurchaseEventChanges_whenGetPurchaseHistoryByProductionCompany_thenReturnsOriginalOrderDetails() {
        // Arrange
        storeMember(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD); //founder
        storeMember(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD); // member that have order
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL); 
        storeManagerInCompanyWithPermissions(VALID_COMPANY_NAME, VALID_EMAIL, MANAGER1_EMAIL,
                EnumSet.of(Permissions.VIEW_PURCHASE_HISTORY));
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        storeFullPurchaseChain(VALID_EMAIL_2, ACTIVE_ORDER_ID_1, ORDER_ID_1,
                event.getEventId(), VALID_SHOW_ID, VALID_TICKET_PRICE, LocalDateTime.now());
        String token = storeMemberAndLogin(MANAGER1_USERNAME, MANAGER1_EMAIL, VALID_PASSWORD);

        List<OrderDTO> beforeResult = assertDoesNotThrow(() ->
                eventService.getPurchaseHistoryByProductionCompany(VALID_COMPANY_NAME, null, null, token));

        // Change event details - price, location, name, show active, production company active
        event.setLocation("Tel Aviv");
        event.setName("Concert");
        event.setActive(false);
        event.setMinPrice(NON_MATCHING_MIN_PRICE);
        event.setMaxPrice(NON_MATCHING_MAX_PRICE);
        eventRepo.store(event);

        // Act
        List<OrderDTO> afterResult = assertDoesNotThrow(() ->
                eventService.getPurchaseHistoryByProductionCompany(VALID_COMPANY_NAME, null, null, token));

        // Assert - original order is returned
        assertNotNull(afterResult);
        assertEquals(1, afterResult.size());
        assertEquals(beforeResult.get(0).getOrderId(), afterResult.get(0).getOrderId());
        assertEquals(beforeResult.get(0).getFinalPrice(), afterResult.get(0).getFinalPrice());
        assertEquals(beforeResult.get(0).getPurchaseTime(), afterResult.get(0).getPurchaseTime());
    }


    ///////// II.4.3 - Configure raffle (sale mode) /////////

    @Test
    void givenFounderWithPermission_whenConfigureRaffle_thenEventBecomesRaffleEvent() {
        // Arrange - founder has all permissions, including EVENT_EDITING
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Act
        eventService.configureRaffle(event.getEventId(), LocalDateTime.now().plusDays(1), Duration.ofMinutes(30), 1, token);

        // Assert
        Event stored = eventRepo.findById(event.getEventId());
        assertTrue(stored.requiresRaffle());
        assertEquals("OPEN", stored.getRaffle().getStatus().name());
        assertEquals(1, stored.getRaffle().getNumberOfWinners());
    }

    @Test
    void givenWinnersExceedingCapacity_whenConfigureRaffle_thenRaffleAttached() {
        // #439: the winner count is independent of capacity, so configuring more winners than seats
        // is allowed (capacity is enforced only at purchase time).
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true); // capacity = VALID_TOTAL_CAPACITY (100)

        eventService.configureRaffle(event.getEventId(), LocalDateTime.now().plusDays(1),
                Duration.ofMinutes(30), VALID_TOTAL_CAPACITY + 1, token);

        Event stored = eventRepo.findById(event.getEventId());
        assertTrue(stored.requiresRaffle());
        assertEquals(VALID_TOTAL_CAPACITY + 1, stored.getRaffle().getNumberOfWinners());
    }

    @Test
    void givenTwoParticipantsAndOneWinner_whenDrawTriggered_thenExactlyOneWins() {
        // Noam's report: two entrants, one winner configured, both were winning. The winner count is the
        // raffle's numberOfWinners (not the full event capacity), so exactly one must win and one must lose.
        Event event = storeEventWithRaffle(VALID_COMPANY_NAME, true);
        event.getRaffle().setNumberOfWinners(1);
        event.getRaffle().register(VALID_EMAIL, LocalDateTime.now());
        event.getRaffle().register(VALID_EMAIL_2, LocalDateTime.now());
        event.getRaffle().setRegistrationDeadline(LocalDateTime.now().minusMinutes(1));
        eventRepo.store(event);

        eventService.ensureRaffleDrawn(event.getEventId());

        assertEquals(1, eventRepo.findById(event.getEventId()).getRaffle().getWinners().size());
        assertEquals(2, notificationRepo.getAll().size()); // 1 winner + 1 loser, both notified
    }

    @Test
    void givenManagerWithEventEditing_whenConfigureRaffle_thenEventBecomesRaffleEvent() {
        // Arrange - manager holds only EVENT_EDITING (the gate now matches event management)
        String token = storeMemberAndLogin(MANAGER1_USERNAME, MANAGER1_EMAIL, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        storeManagerInCompanyWithPermissions(VALID_COMPANY_NAME, VALID_EMAIL, MANAGER1_EMAIL, EVENT_EDITING_PERMISSIONS);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Act
        eventService.configureRaffle(event.getEventId(), LocalDateTime.now().plusDays(1), Duration.ofMinutes(30), 1, token);

        // Assert
        assertTrue(eventRepo.findById(event.getEventId()).requiresRaffle());
    }

    @Test
    void givenMemberWithoutPermission_whenConfigureRaffle_thenThrowsUnauthorized() {
        // Arrange - VALID_EMAIL_2 holds no role in the company
        String token = storeMemberAndLogin(VALID_USERNAME_2, VALID_EMAIL_2, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Act & Assert
        assertThrows(UnauthorizedException.class, () ->
                eventService.configureRaffle(event.getEventId(), LocalDateTime.now().plusDays(1), Duration.ofMinutes(30), 1, token));
        assertFalse(eventRepo.findById(event.getEventId()).requiresRaffle());
    }

    @Test
    void givenPastDeadline_whenConfigureRaffle_thenThrowsInvalidEvent() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Act & Assert
        assertThrows(InvalidEventException.class, () ->
                eventService.configureRaffle(event.getEventId(), LocalDateTime.now().minusDays(1), Duration.ofMinutes(30), 1, token));
    }

    @Test
    void givenEvent_whenConfigureRaffle_thenRaffleAttached() {
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        eventService.configureRaffle(event.getEventId(), LocalDateTime.now().plusDays(1), Duration.ofMinutes(30), 1, token);

        assertTrue(eventRepo.findById(event.getEventId()).requiresRaffle());
    }

    ///////// UC10 - Register for Raffle /////////

    @Test
    void givenLoggedInMemberAndOpenRaffle_whenRegisterForRaffle_thenParticipantAdded() {
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        Event event = storeEventWithRaffle(VALID_COMPANY_NAME, true);

        assertDoesNotThrow(() -> eventService.registerForRaffle(event.getEventId(), token));

        assertTrue(eventRepo.findById(event.getEventId()).getRaffle().isParticipant(VALID_EMAIL));
    }

    @Test
    void givenGuest_whenRegisterForRaffle_thenThrowsUnauthorized() {
        Event event = storeEventWithRaffle(VALID_COMPANY_NAME, true);

        assertThrows(UnauthorizedException.class, () -> eventService.registerForRaffle(event.getEventId(), null));

        assertTrue(eventRepo.findById(event.getEventId()).getRaffle().getParticipants().isEmpty());
    }

    @Test
    void givenEventWithoutRaffle_whenRegisterForRaffle_thenThrowsRaffleException() {
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        assertThrows(RaffleException.class, () -> eventService.registerForRaffle(event.getEventId(), token));
    }

    @Test
    void givenDuplicateRegistration_whenRegisterForRaffle_thenThrows() {
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        Event event = storeEventWithRaffle(VALID_COMPANY_NAME, true);
        event.getRaffle().register(VALID_EMAIL, LocalDateTime.now());
        eventRepo.store(event);

        assertThrows(RaffleException.class, () -> eventService.registerForRaffle(event.getEventId(), token));
        assertEquals(1, eventRepo.findById(event.getEventId()).getRaffle().getParticipants().size());
    }

    @Test
    void givenRegistrationDeadlinePassed_whenRegisterForRaffle_thenThrows() {
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        Event event = storeEventWithRaffle(VALID_COMPANY_NAME, true);
        event.getRaffle().setRegistrationDeadline(LocalDateTime.now().minusMinutes(1));
        eventRepo.store(event);

        assertThrows(RaffleException.class, () -> eventService.registerForRaffle(event.getEventId(), token));
    }

    ///////// get raffle status /////////

    @Test
    void givenSaleNotOpen_whenGetRaffleStatus_thenNoDrawAndStatusOpen() {
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        Event event = storeEventWithRaffle(VALID_COMPANY_NAME, true); // deadline in the future
        event.getRaffle().register(VALID_EMAIL, LocalDateTime.now());
        eventRepo.store(event);

        RaffleStatusDTO status = eventService.getRaffleStatus(event.getEventId(), token);

        assertEquals("OPEN", status.status);
        assertTrue(status.registered);
        assertFalse(status.won);
        assertNull(status.code);
    }

    ///////// ensure raffle drawn /////////

    @Test
    void givenOnlyAnEventId_whenDrawTriggered_thenLoadsFromRepoAndDrawsAndNotifies() {
        // Regression guard for the draw acting on a stale/detached entity (under the JPA profile a
        // detached event's lazy raffle throws LazyInitializationException). The contract is id-only:
        // the draw must re-load the event from the repository, so callers never hand it an entity.
        Event event = storeEventWithRaffle(VALID_COMPANY_NAME, true);
        event.getRaffle().register(VALID_EMAIL, LocalDateTime.now());
        event.getRaffle().register(VALID_EMAIL_2, LocalDateTime.now());
        event.getRaffle().setRegistrationDeadline(LocalDateTime.now().minusMinutes(1)); // sale opened
        eventRepo.store(event);
        String eventId = event.getEventId();

        // Act - drive the draw with the id alone, exactly as RaffleDrawScheduler does
        eventService.ensureRaffleDrawn(eventId);

        // Assert - the event was loaded from the repo, drawn, and every participant notified
        Event drawn = eventRepo.findById(eventId);
        assertEquals(RaffleStatus.DRAWN, drawn.getRaffle().getStatus());
        assertEquals(2, drawn.getRaffle().getWinners().size());
        assertEquals(2, notificationRepo.getAll().size());
    }

    @Test
    void givenSaleOpenWithParticipants_whenDrawTriggered_thenDrawsOnceAndNotifiesWinners() {
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        Event event = storeEventWithRaffle(VALID_COMPANY_NAME, true);
        event.getRaffle().register(VALID_EMAIL, LocalDateTime.now());
        event.getRaffle().register(VALID_EMAIL_2, LocalDateTime.now());
        event.getRaffle().setRegistrationDeadline(LocalDateTime.now().minusMinutes(1)); // sale opened
        eventRepo.store(event);

        // Act - the real-time scheduler drives the draw once the sale has opened
        eventService.ensureRaffleDrawn(event.getEventId());

        // Assert - drawn, both participants win (capacity 100), both notified; status reflects the win
        RaffleStatusDTO status = eventService.getRaffleStatus(event.getEventId(), token);
        assertEquals("DRAWN", status.status);
        assertTrue(status.won);
        assertNotNull(status.code);
        assertEquals(2, eventRepo.findById(event.getEventId()).getRaffle().getWinners().size());
        assertEquals(2, notificationRepo.getAll().size());

        // Act again - idempotent, no second draw / no extra notifications
        eventService.ensureRaffleDrawn(event.getEventId());
        assertEquals(2, notificationRepo.getAll().size());
    }

    @Test
    void givenMoreParticipantsThanWinners_whenDrawTriggered_thenWinnersCappedAtWinnerCount() {
        Event event = storeEventWithRaffle(VALID_COMPANY_NAME, true);
        event.getRaffle().setNumberOfWinners(1);
        event.getRaffle().register(VALID_EMAIL, LocalDateTime.now());
        event.getRaffle().register(VALID_EMAIL_2, LocalDateTime.now());
        event.getRaffle().register(VALID_EMAIL_3, LocalDateTime.now());
        event.getRaffle().setRegistrationDeadline(LocalDateTime.now().minusMinutes(1));
        eventRepo.store(event);

        eventService.ensureRaffleDrawn(event.getEventId());

        assertEquals(1, eventRepo.findById(event.getEventId()).getRaffle().getWinners().size());
    }

    @Test
    void givenMoreParticipantsThanWinners_whenDrawTriggered_thenWinnerAndLosersBothNotified() {
        Event event = storeEventWithRaffle(VALID_COMPANY_NAME, true);
        event.getRaffle().setNumberOfWinners(1);
        event.getRaffle().register(VALID_EMAIL, LocalDateTime.now());
        event.getRaffle().register(VALID_EMAIL_2, LocalDateTime.now());
        event.getRaffle().register(VALID_EMAIL_3, LocalDateTime.now());
        event.getRaffle().setRegistrationDeadline(LocalDateTime.now().minusMinutes(1));
        eventRepo.store(event);

        eventService.ensureRaffleDrawn(event.getEventId());

        // 1 winner + 2 non-winners: every participant is notified of the outcome
        assertEquals(1, eventRepo.findById(event.getEventId()).getRaffle().getWinners().size());
        assertEquals(3, notificationRepo.getAll().size());
    }

    ///////// Raffle concurrency /////////

    @Test
    void givenManyConcurrentRegistrations_whenRegisterForRaffle_thenAllRecordedWithoutLoss() throws InterruptedException {
        Event event = storeEventWithRaffle(VALID_COMPANY_NAME, true);
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final String memberEmail = "racer" + i + "@mail.com"; // token == email under StubAuth
            pool.submit(() -> {
                try {
                    start.await();
                    eventService.registerForRaffle(event.getEventId(), memberEmail);
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(threads, eventRepo.findById(event.getEventId()).getRaffle().getParticipants().size());
    }

    @Test
    void givenConcurrentAccessAtSaleOpen_whenDrawTriggered_thenDrawnExactlyOnce() throws InterruptedException {
        Event event = storeEventWithRaffle(VALID_COMPANY_NAME, true);
        event.getRaffle().register(VALID_EMAIL, LocalDateTime.now());
        event.getRaffle().register(VALID_EMAIL_2, LocalDateTime.now());
        event.getRaffle().register(VALID_EMAIL_3, LocalDateTime.now());
        event.getRaffle().setRegistrationDeadline(LocalDateTime.now().minusMinutes(1));
        eventRepo.store(event);

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    eventService.ensureRaffleDrawn(event.getEventId());
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(0, failures.get());
        // Exactly one draw: 3 winners, and each notified exactly once (no duplicate draws).
        assertEquals(3, eventRepo.findById(event.getEventId()).getRaffle().getWinners().size());
        assertEquals(3, notificationRepo.getAll().size());
    }

    ///////// get event by id (public)
    @Test
    void givenExistingEvent_whenGetEventById_thenReturnsEventDTO() {
        // Arrange
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Act
        EventDTO result = assertDoesNotThrow(() -> eventService.getEventById(event.getEventId()));

        // Assert
        assertNotNull(result);
        assertEquals(event.getEventId(), result.eventId);
    }

    @Test
    void givenNonExistentEvent_whenGetEventById_thenThrowsEventNotFoundException() {
        // Act & Assert
        assertThrows(EventNotFoundException.class, () -> eventService.getEventById(NON_EXISTENT_EVENT_ID));

        // Assert - internal state
        assertEquals(0, eventRepo.getAll().size());
    }

    //// UC16  set purchase & discount Policies

    @Test
    void givenValidEventAndPurchasePolicyByFounder_whenSetEventPurchasePolicy_thenPolicyIsApplied() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Act
        EventDTO result = assertDoesNotThrow(() ->
                eventService.setEventPurchasePolicy(event.getEventId(), MIN_AGE_18_POLICY, token));

        // Assert
        assertNotNull(result);
        assertEquals(event.getEventId(), result.eventId);

        // Assert - database internal state update verified
        Event storedEvent = eventRepo.findById(event.getEventId());
        assertNotNull(storedEvent);
        assertNotNull(storedEvent.getPurchasePolicy());
    }

    @Test
    void givenNonExistentEvent_whenSetEventPurchasePolicy_thenThrowsEventNotFoundException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        // Act & Assert - should fail immediately because event does not exist
        assertThrows(EventNotFoundException.class, () ->
                eventService.setEventPurchasePolicy(NON_EXISTENT_EVENT_ID, MIN_AGE_18_POLICY, token));
    }

    @Test
    void givenValidEventAndDiscountPolicyByValidManager_whenSetDiscountPolicy_thenPolicyIsApplied() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL,  EnumSet.of(Permissions.CHANGE_DISCOUNT_POLICY));
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Act
        EventDTO result = assertDoesNotThrow(() ->
                eventService.setDiscountPolicy(event.getEventId(), DISCOUNT_COMPOSITE_MAX, token));

        // Assert
        assertNotNull(result);
        assertEquals(event.getEventId(), result.eventId);

        // Assert - database internal state update verified
        Event storedEvent = eventRepo.findById(event.getEventId());
        assertNotNull(storedEvent);
        assertNotNull(storedEvent.getDiscountPolicy());
    }

    @Test
    void givenNonExistentEvent_whenSetDiscountPolicy_thenThrowsEventNotFoundException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        // Act & Assert - should fail immediately because event does not exist
        assertThrows(EventNotFoundException.class, () ->
                eventService.setDiscountPolicy(NON_EXISTENT_EVENT_ID, DISCOUNT_COMPOSITE_MAX, token));
    }
    @Test
    void givenManagerWithoutEventEditingPermission_whenSetEventPurchasePolicy_thenThrowsUnauthorizedException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);

        storeManagerInCompany(company.getName(), company.getFounderID(), VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Act & Assert
        // Should throw UnauthorizedException because the user lacks the required gate permissions
        assertThrows(UnauthorizedException.class, () ->
                eventService.setEventPurchasePolicy(event.getEventId(), MIN_AGE_18_POLICY, token));
    }

    @Test
    void givenManagerWithoutEventEditingPermission_whenSetDiscountPolicy_thenThrowsUnauthorizedException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);

        storeManagerInCompany(company.getName(), company.getFounderID(), VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        // Act & Assert
        assertThrows(UnauthorizedException.class, () ->
                eventService.setDiscountPolicy(event.getEventId(), DISCOUNT_COMPOSITE_MAX, token));
    }
}

