package acceptance;

import application.dto.SeatDTO;
import application.dto.ShowDTO;
import application.dto.VenueMapDTO;
import application.dto.ZoneDTO;
import application.service.EventService;
import application.service.ShowService;
import domain.Aggregates.event.Event;
import domain.Aggregates.productioncompany.ProductionCompany;
import domain.Aggregates.show.Seat;
import domain.Aggregates.show.Show;
import domain.Aggregates.show.VenueMap;
import domain.Aggregates.show.Zone;
import domain.exceptions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ShowServiceTest extends AcceptanceTestBase {

    private ShowService showService;

    @BeforeEach
    void setUp() {
        EventService eventService = new EventService(eventRepo, authService, companyRepo,
                notificationService, orderRepo, showRepo, discountPolicyFactory, purchasePolicyFactory, pubEvent -> {});
        showService = new ShowService(showRepo, authService, eventService);
    }

    ///////// UC4 - View Show Map /////////

    @Test
    void givenShowWithVenueMap_whenGetHallLayout_thenReturnsVenueMapDTO() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        showRepo.saveVenueMapLayout(show.getShowId(), show.getVenueMap());

        VenueMapDTO result = assertDoesNotThrow(() -> showService.getHallLayout(show.getShowId()));

        assertNotNull(result);
        assertFalse(result.zones.isEmpty());
    }

    @Test
    void givenNonExistentShowId_whenGetHallLayout_thenThrowsVenueMapNotFoundException() {
        assertThrows(VenueMapNotFoundException.class,
                () -> showService.getHallLayout("non-existent-show"));
    }

    @Test
    void givenShowWithSeatedZone_whenGetHallLayout_thenDTOContainsCorrectSeatData() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        showRepo.saveVenueMapLayout(show.getShowId(), show.getVenueMap());

        VenueMapDTO result = showService.getHallLayout(show.getShowId());

        assertEquals(show.getShowId(), result.showId);
        assertEquals(1, result.zones.size());

        ZoneDTO zone = result.zones.get(0);
        assertEquals(VALID_ZONE_ID, zone.zoneId);
        assertFalse(zone.standing);
        assertEquals(1, zone.seats.size());

        SeatDTO seat = zone.seats.get(0);
        assertEquals(VALID_SEAT_ID, seat.seatId);
        assertEquals(1, seat.rowNumber);
        assertEquals(1, seat.seatNumber);
        assertFalse(seat.isTaken);
    }

    @Test
    void givenShowWithStandingZone_whenGetHallLayout_thenDTOContainsCorrectStandingData() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithStandingZone(event.getEventId(), VALID_ZONE_ID, 100);
        showRepo.saveVenueMapLayout(show.getShowId(), show.getVenueMap());

        VenueMapDTO result = showService.getHallLayout(show.getShowId());

        assertEquals(1, result.zones.size());

        ZoneDTO zone = result.zones.get(0);
        assertEquals(VALID_ZONE_ID, zone.zoneId);
        assertTrue(zone.standing);
        assertEquals(100, zone.standingCapacity);
        assertEquals(100, zone.standingAvailable);
        assertTrue(zone.seats.isEmpty());
    }

    @Test
    void givenShowWithMultipleZones_whenGetHallLayout_thenAllZonesReturnedWithCorrectCount() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        String secondZoneId = "zone2";

        Zone seatedZone = Zone.builder()
                .zoneId(VALID_ZONE_ID).standing(false).build();
        seatedZone.addSeat(Seat.builder()
                .id(VALID_SEAT_ID).row(1).number(1).price(100.0).build());

        Zone standingZone = Zone.builder()
                .zoneId(secondZoneId).standing(true)
                .standingCapacity(50).standingAvailable(50).build();

        Show show = Show.builder().eventId(event.getEventId()).active(true).build();
        showRepo.store(show);

        VenueMap venueMap = VenueMap.builder()
                .showId(show.getShowId()).zoneCount(2).build();
        venueMap.addZone(seatedZone);
        venueMap.addZone(standingZone);
        show.setVenueMap(venueMap);
        showRepo.store(show);
        showRepo.saveVenueMapLayout(show.getShowId(), venueMap);

        VenueMapDTO result = showService.getHallLayout(show.getShowId());

        assertEquals(2, result.zoneCount);
        assertEquals(2, result.zones.size());
        assertTrue(result.zones.stream().anyMatch(z -> z.zoneId.equals(VALID_ZONE_ID)));
        assertTrue(result.zones.stream().anyMatch(z -> z.zoneId.equals(secondZoneId)));
    }

    @Test
    void givenShowWithEmptyVenueMap_whenGetHallLayout_thenReturnsDTOWithEmptyZones() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);

        Show show = Show.builder().eventId(event.getEventId()).active(true).build();
        showRepo.store(show);

        VenueMap venueMap = VenueMap.builder()
                .showId(show.getShowId()).build();
        showRepo.saveVenueMapLayout(show.getShowId(), venueMap);

        VenueMapDTO result = assertDoesNotThrow(() -> showService.getHallLayout(show.getShowId()));

        assertNotNull(result);
        assertTrue(result.zones.isEmpty());
    }

    @Test
    void givenInactiveShowWithVenueMap_whenGetHallLayout_thenReturnsVenueMapDTO() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShowWithSeat(event.getEventId(), VALID_ZONE_ID, VALID_SEAT_ID);
        show.setActive(false);
        showRepo.store(show);
        showRepo.saveVenueMapLayout(show.getShowId(), show.getVenueMap());

        VenueMapDTO result = assertDoesNotThrow(() -> showService.getHallLayout(show.getShowId()));

        assertNotNull(result);
        assertFalse(result.zones.isEmpty());
    }

    @Test
    void givenNoVenueMapAndValidToken_whenGetHallLayoutWithToken_thenThrowsVenueMapNotFoundException() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true);

        assertThrows(VenueMapNotFoundException.class,
                () -> showService.getHallLayout(show.getShowId()));
    }


    @Test
    void givenShowWithoutVenueMap_whenGetHallLayout_thenThrowsVenueMapNotFoundException() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true);
        assertThrows(VenueMapNotFoundException.class, () -> showService.getHallLayout(show.getShowId()));
    }

    ///////// Delete Show /////////

    @Test
    void givenExistingShow_whenRemoveShow_thenShowIsDeleted() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        assertDoesNotThrow(() -> showService.removeShow(show.getShowId(), token));

        assertNull(showRepo.findById(show.getShowId()));
    }

    @Test
    void givenNonExistentShowId_whenRemoveShow_thenThrowsShowNotFoundException() {
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        assertThrows(ShowNotFoundException.class,
                () -> showService.removeShow("non-existent-show", token));
    }

    @Test
    void givenNullToken_whenRemoveShow_thenThrowsUnauthorizedException() {
        assertThrows(UnauthorizedException.class,
                () -> showService.removeShow(VALID_SHOW_ID, null));
    }


    ///////// Edit Show /////////

    @Test
    void givenExistingShowWithValidZones_whenEditShow_thenCapacityIsCalculatedCorrectly() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        ZoneDTO validZone = new ZoneDTO();
        validZone.standing = false;
        validZone.rows = 3;
        validZone.seatsPerRow = 5;
        validZone.basePrice = 50.0;

        ShowDTO dto = new ShowDTO();
        dto.date = VALID_END_DATE;
        dto.zones = List.of(validZone);

        ShowDTO result = assertDoesNotThrow(() -> showService.editShow(show.getShowId(), dto, token));

        assertEquals(15, result.totalCapacity);
        assertEquals(15, showRepo.findById(show.getShowId()).getTotalCapacity());
    }

    @Test
    void givenExistingShow_whenEditShow_thenShowIsUpdated() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        ShowDTO dto = new ShowDTO();
        dto.date = VALID_END_DATE;
        dto.zones = null;

        ShowDTO result = assertDoesNotThrow(() -> showService.editShow(show.getShowId(), dto, token));

        assertEquals(VALID_END_DATE, result.date);
        assertEquals(VALID_END_DATE, showRepo.findById(show.getShowId()).getDate());
    }

    @Test
    void givenNullToken_whenEditShow_thenThrowsUnauthorizedException() {
        ShowDTO dto = new ShowDTO();
        dto.date = VALID_END_DATE;

        assertThrows(UnauthorizedException.class,
                () -> showService.editShow(VALID_SHOW_ID, null, null));
    }

    @Test
    void givenNonExistentShowId_whenEditShow_thenThrowsShowNotFoundException() {
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        ShowDTO dto = new ShowDTO();
        dto.date = VALID_END_DATE;

        assertThrows(ShowNotFoundException.class,
                () -> showService.editShow("non-existent-show", dto, token));
    }

    @Test
    void givenNullDate_whenEditShow_thenThrowsInvalidShowException() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        ShowDTO dto = new ShowDTO();
        dto.date = null;

        assertThrows(InvalidShowException.class,
                () -> showService.editShow(show.getShowId(), dto, token));
    }

    @Test
    void givenPastDate_whenEditShow_thenThrowsInvalidShowException() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        ShowDTO dto = new ShowDTO();
        dto.date = VALID_START_DATE;

        assertThrows(InvalidShowException.class,
                () -> showService.editShow(show.getShowId(), dto, token));
    }

    @Test
    void givenNegativeBasePrice_whenEditShow_thenThrowsInvalidShowException() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        ZoneDTO zone = new ZoneDTO();
        zone.standing = false;
        zone.rows = 2;
        zone.seatsPerRow = 5;
        zone.basePrice = -1.0;

        ShowDTO dto = new ShowDTO();
        dto.date = VALID_END_DATE;
        dto.zones = List.of(zone);

        assertThrows(InvalidShowException.class,
                () -> showService.editShow(show.getShowId(), dto, token));
    }

    @Test
    void givenNegativeStandingCapacity_whenEditShow_thenThrowsInvalidShowException() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        ZoneDTO zone = new ZoneDTO();
        zone.standing = true;
        zone.standingCapacity = -1;
        zone.basePrice = 50.0;

        ShowDTO dto = new ShowDTO();
        dto.date = VALID_END_DATE;
        dto.zones = List.of(zone);

        assertThrows(InvalidShowException.class,
                () -> showService.editShow(show.getShowId(), dto, token));
    }

    @Test
    void givenNegativeRows_whenEditShow_thenThrowsInvalidShowException() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        ZoneDTO zone = new ZoneDTO();
        zone.standing = false;
        zone.rows = -1;
        zone.seatsPerRow = 5;
        zone.basePrice = 50.0;

        ShowDTO dto = new ShowDTO();
        dto.date = VALID_END_DATE;
        dto.zones = List.of(zone);

        assertThrows(InvalidShowException.class,
                () -> showService.editShow(show.getShowId(), dto, token));
    }


    /////// create show ///////

    @Test
    void givenValidShowDTO_whenCreateShow_thenShowIsStoredAndDTOIsReturned() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        ShowDTO dto = new ShowDTO();
        dto.date = VALID_END_DATE;
        dto.zones = null;

        ShowDTO result = assertDoesNotThrow(() -> showService.createShow(event.getEventId(), dto, token));

        assertEquals(event.getEventId(), result.eventId);
        assertEquals(VALID_END_DATE, result.date);
        assertEquals(0, result.totalCapacity);
        assertNotNull(showRepo.findById(result.showId));
    }

    @Test
    void givenShowDTOWithSeatedZone_whenCreateShow_thenTotalCapacityCalculatedFromRowsAndSeats() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        ZoneDTO zone = new ZoneDTO();
        zone.standing = false;
        zone.rows = 3;
        zone.seatsPerRow = 5;
        zone.basePrice = 50.0;

        ShowDTO dto = new ShowDTO();
        dto.date = VALID_END_DATE;
        dto.zones = List.of(zone);

        ShowDTO result = assertDoesNotThrow(() -> showService.createShow(event.getEventId(), dto, token));

        assertEquals(15, result.totalCapacity);
    }

    @Test
    void givenShowDTOWithStandingZone_whenCreateShow_thenTotalCapacityCalculatedFromStandingCapacity() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        ZoneDTO zone = new ZoneDTO();
        zone.standing = true;
        zone.standingCapacity = 50;
        zone.basePrice = 30.0;

        ShowDTO dto = new ShowDTO();
        dto.date = VALID_END_DATE;
        dto.zones = List.of(zone);

        ShowDTO result = assertDoesNotThrow(() -> showService.createShow(event.getEventId(), dto, token));

        assertEquals(50, result.totalCapacity);
    }


    @Test
    void givenShowAlreadyExistsForEventAndDate_whenCreateShow_thenThrowsDuplicateShowException() {
        storeCompany(VALID_COMPANY_NAME, VALID_EMAIL);
        Event event = storeEvent(VALID_COMPANY_NAME, true);
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        ShowDTO dto = new ShowDTO();
        dto.date = VALID_END_DATE;
        dto.zones = null;

        showService.createShow(event.getEventId(), dto, token);

        assertThrows(DuplicateShowException.class,
                () -> showService.createShow(event.getEventId(), dto, token));
    }


    @Test
    void givenNullToken_whenCreateShow_thenThrowsUnauthorizedException() {
        ShowDTO dto = new ShowDTO();
        dto.date = VALID_END_DATE;

        assertThrows(UnauthorizedException.class,
                () -> showService.createShow(VALID_EVENT_ID, dto, null));
    }

    @Test
    void givenNullDate_whenCreateShow_thenThrowsInvalidShowException() {
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);

        ShowDTO dto = new ShowDTO();
        dto.date = null;

        assertThrows(InvalidShowException.class,
                () -> showService.createShow(VALID_EVENT_ID, dto, token));
    }



    ///////// UC15 - Define Hall Layout /////////

    @Test
    void givenShowWithMatchingVenueMap_whenDefineHallLayout_thenMapIsSavedAndLinkedToShow() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS); // Add manager with EVENT_EDITING_PERMISSIONS permission

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 100); // Create show with 100 total capacity
        VenueMapDTO venueMapDTO = createSeatedVenueMapDTO(show.getShowId(), 10, 10, 50.0); // Create venue map with 10*10 seats

        // Act
        VenueMapDTO result = assertDoesNotThrow(() ->
                showService.defineHallLayout(show.getShowId(), venueMapDTO, token));

        // Assert - return value
        assertNotNull(result);
        assertEquals(100, result.totalCapacity);
        assertEquals(1, result.zones.size());

        // Assert - persisted in repository, linked to show
        assertNotNull(showRepo.getVenueMapByShowId(show.getShowId()));
        assertEquals(100, showRepo.getVenueMapByShowId(show.getShowId()).getTotalCapacity());
    }


    @Test
    void givenNonExistentShow_whenDefineHallLayout_thenThrowsShowNotFoundException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        VenueMapDTO venueMapDTO = createSeatedVenueMapDTO(NON_EXISTENT_SHOW_ID, 10, 10, 50.0);

        // Act & Assert
        assertThrows(ShowNotFoundException.class,
                () -> showService.defineHallLayout(NON_EXISTENT_SHOW_ID, venueMapDTO, token));

        // Assert - no venue map was saved and no show was found
        assertNull(showRepo.findById(NON_EXISTENT_SHOW_ID));
        assertNull(showRepo.getVenueMapByShowId(NON_EXISTENT_SHOW_ID));
    }

    @Test
    void givenShowWithExistingVenueMap_whenDefineHallLayout_thenThrowsVenueMapAlreadyExistsException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 100);
        showRepo.saveVenueMapLayout(show.getShowId(), new VenueMap()); // Venue map already defined

        VenueMapDTO venueMapDTO = createSeatedVenueMapDTO(show.getShowId(), 10, 10, 50.0);

        // Act & Assert
        assertThrows(VenueMapAlreadyExistsException.class,
                () -> showService.defineHallLayout(show.getShowId(), venueMapDTO, token));

        // Assert - original venue map was not overwritten (still empty, totalCapacity=0)
        assertEquals(0, showRepo.getVenueMapByShowId(show.getShowId()).getTotalCapacity());
    }

    @Test
    void givenManagerWithoutEventEditingPermission_whenDefineHallLayout_thenThrowsException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompany(company.getName(), company.getFounderID(), VALID_EMAIL); // Default permissions, no EVENT_EDITING permission

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 100);
        VenueMapDTO venueMapDTO = createSeatedVenueMapDTO(show.getShowId(), 10, 10, 50.0);

        // Act & Assert
        assertThrows(Exception.class,
                () -> showService.defineHallLayout(show.getShowId(), venueMapDTO, token));

        // Assert - no venue map was saved
        assertNull(showRepo.getVenueMapByShowId(show.getShowId()));
    }

    ///////// Remove Hall Layout /////////

    @Test
    void givenShowWithVenueMap_whenRemoveHallLayout_thenMapIsDeletedAndUnlinkedFromShow() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 100);
        showRepo.saveVenueMapLayout(show.getShowId(), new VenueMap());

        // Act
        assertDoesNotThrow(() -> showService.removeHallLayout(show.getShowId(), token));

        // Assert - venue map was removed from repository, show still exists
        assertNull(showRepo.getVenueMapByShowId(show.getShowId()));
        assertNotNull(showRepo.findById(show.getShowId()));
    }

    @Test
    void givenShowWithNoVenueMap_whenRemoveHallLayout_thenThrowsVenueMapNotFoundException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 100); // No venue map saved

        // Act & Assert
        assertThrows(VenueMapNotFoundException.class,
                () -> showService.removeHallLayout(show.getShowId(), token));

        // Assert - show still exists, no map was ever there
        assertNotNull(showRepo.findById(show.getShowId()));
        assertNull(showRepo.getVenueMapByShowId(show.getShowId()));
    }

    @Test
    void givenNonExistentShow_whenRemoveHallLayout_thenThrowsShowNotFoundException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        // Act & Assert
        assertThrows(ShowNotFoundException.class,
                () -> showService.removeHallLayout(NON_EXISTENT_SHOW_ID, token));

        // Assert - nothing was changed
        assertNull(showRepo.findById(NON_EXISTENT_SHOW_ID));
        assertNull(showRepo.getVenueMapByShowId(NON_EXISTENT_SHOW_ID));
    }

    @Test
    void givenManagerWithoutEventEditingPermission_whenRemoveHallLayout_thenThrowsException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompany(company.getName(), company.getFounderID(), VALID_EMAIL); // Default permissions, no

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 100);
        showRepo.saveVenueMapLayout(show.getShowId(), new VenueMap());

        // Act & Assert
        assertThrows(Exception.class,
                () -> showService.removeHallLayout(show.getShowId(), token));

        // Assert - venue map was not removed
        assertNotNull(showRepo.getVenueMapByShowId(show.getShowId()));
    }

    ///////// Edit Hall Layout /////////

    @Test
    void givenShowWithVenueMapAndMatchingCapacity_whenUpdateHallLayout_thenMapIsUpdatedForShow() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 100);
        showRepo.saveVenueMapLayout(show.getShowId(), new VenueMap()); // Existing venue map
        VenueMapDTO updatedMap = createSeatedVenueMapDTO(show.getShowId(), 10, 10, 75.0); // New map, same capacity

        // Act
        VenueMapDTO result = assertDoesNotThrow(() ->
                showService.updateHallLayout(show.getShowId(), updatedMap, token));

        // Assert - return value
        assertNotNull(result);
        assertEquals(100, result.totalCapacity);
        assertEquals(1, result.zones.size());

        // Assert - persisted in repository with updated data
        assertEquals(100, showRepo.getVenueMapByShowId(show.getShowId()).getTotalCapacity());
    }

    @Test
    void givenNonExistentShow_whenUpdateHallLayout_thenThrowsShowNotFoundException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        VenueMapDTO venueMapDTO = createSeatedVenueMapDTO(NON_EXISTENT_SHOW_ID, 10, 10, 50.0);

        // Act & Assert
        assertThrows(ShowNotFoundException.class,
                () -> showService.updateHallLayout(NON_EXISTENT_SHOW_ID, venueMapDTO, token));

        // Assert - nothing was changed
        assertNull(showRepo.findById(NON_EXISTENT_SHOW_ID));
        assertNull(showRepo.getVenueMapByShowId(NON_EXISTENT_SHOW_ID));
    }

    @Test
    void givenShowWithNoVenueMap_whenUpdateHallLayout_thenThrowsVenueMapNotFoundException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 100); // No venue map saved
        VenueMapDTO venueMapDTO = createSeatedVenueMapDTO(show.getShowId(), 10, 10, 50.0);

        // Act & Assert
        assertThrows(VenueMapNotFoundException.class,
                () -> showService.updateHallLayout(show.getShowId(), venueMapDTO, token));

        // Assert - show still exists, no map was saved
        assertNotNull(showRepo.findById(show.getShowId()));
        assertNull(showRepo.getVenueMapByShowId(show.getShowId()));
    }

    @Test
    void givenManagerWithoutEventEditingPermission_whenUpdateHallLayout_thenThrowsException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompany(company.getName(), company.getFounderID(), VALID_EMAIL); // Default permissions, no EVENT_EDITING permission

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 100);
        showRepo.saveVenueMapLayout(show.getShowId(), new VenueMap());
        VenueMapDTO venueMapDTO = createSeatedVenueMapDTO(show.getShowId(), 10, 10, 50.0);

        // Act & Assert
        assertThrows(Exception.class,
                () -> showService.updateHallLayout(show.getShowId(), venueMapDTO, token));

        // Assert - venue map was not updated (still empty, totalCapacity=0)
        assertEquals(0, showRepo.getVenueMapByShowId(show.getShowId()).getTotalCapacity());
    }

    ///////// Prevent layout reduction below already purchased/reserved seats /////////

    @Test
    void givenVenueMapWithSoldSeats_whenUpdateHallLayout_thenThrowsInvalidShowException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 64);                                   // owner tried to shrink inventory to 8x8
        showRepo.saveVenueMapLayout(show.getShowId(), occupiedSeatedMap(show.getShowId(), 10, 10, 80)); // 80 of 100 seats sold
        VenueMapDTO shrunkMap = createSeatedVenueMapDTO(show.getShowId(), 8, 8, 50.0);         // 64 seats < 80 sold

        // Act & Assert
        assertThrows(InvalidShowException.class,
                () -> showService.updateHallLayout(show.getShowId(), shrunkMap, token));

        // Assert - original layout is untouched (still 100 capacity, 80 occupied)
        VenueMap preserved = showRepo.getVenueMapByShowId(show.getShowId());
        assertEquals(100, preserved.getTotalCapacity());
        assertEquals(80, preserved.getOccupiedCapacity());
    }

    @Test
    void givenStandingZoneWithReservedTickets_whenUpdateHallLayout_thenThrowsInvalidShowException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 50);
        showRepo.saveVenueMapLayout(show.getShowId(), occupiedStandingMap(show.getShowId(), 50, 20)); // 30 reserved
        VenueMapDTO newMap = createSeatedVenueMapDTO(show.getShowId(), 5, 10, 40.0);                  // 50 capacity

        // Act & Assert
        assertThrows(InvalidShowException.class,
                () -> showService.updateHallLayout(show.getShowId(), newMap, token));

        // Assert - reserved standing tickets are preserved
        assertEquals(30, showRepo.getVenueMapByShowId(show.getShowId()).getOccupiedCapacity());
    }

    @Test
    void givenVenueMapWithNoSoldSeats_whenUpdateHallLayout_thenMapIsUpdated() {
        // Arrange - a map exists with seats, but none are sold/reserved yet (pre-sale editing stays allowed)
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 100);
        showRepo.saveVenueMapLayout(show.getShowId(), occupiedSeatedMap(show.getShowId(), 10, 10, 0)); // nothing sold
        VenueMapDTO updatedMap = createSeatedVenueMapDTO(show.getShowId(), 10, 10, 75.0);

        // Act
        VenueMapDTO result = assertDoesNotThrow(() ->
                showService.updateHallLayout(show.getShowId(), updatedMap, token));

        // Assert - update succeeds and is persisted
        assertNotNull(result);
        assertEquals(100, result.totalCapacity);
        assertEquals(100, showRepo.getVenueMapByShowId(show.getShowId()).getTotalCapacity());
    }

    @Test
    void givenVenueMapWithSoldSeats_whenRemoveHallLayout_thenThrowsInvalidShowException() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 100);
        showRepo.saveVenueMapLayout(show.getShowId(), occupiedSeatedMap(show.getShowId(), 10, 10, 1)); // 1 seat sold

        // Act & Assert
        assertThrows(InvalidShowException.class,
                () -> showService.removeHallLayout(show.getShowId(), token));

        // Assert - layout is not removed
        assertNotNull(showRepo.getVenueMapByShowId(show.getShowId()));
    }

    @Test
    void givenVenueMapWithNoSoldSeats_whenRemoveHallLayout_thenMapIsRemoved() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 100);
        showRepo.saveVenueMapLayout(show.getShowId(), occupiedSeatedMap(show.getShowId(), 10, 10, 0)); // nothing sold

        // Act
        assertDoesNotThrow(() -> showService.removeHallLayout(show.getShowId(), token));

        // Assert - layout is removed
        assertNull(showRepo.getVenueMapByShowId(show.getShowId()));
    }

    ///////// Unique zone/seat IDs across builds (prevents JPA primary-key collisions) /////////

    @Test
    void givenDefinedLayout_whenUpdated_thenZoneAndSeatIdsAreDisjointFromPreviousBuild() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 100);

        VenueMapDTO first = showService.defineHallLayout(show.getShowId(),
                createSeatedVenueMapDTO(show.getShowId(), 10, 10, 50.0), token);

        // Act - re-build the layout (same shape/capacity)
        VenueMapDTO second = showService.updateHallLayout(show.getShowId(),
                createSeatedVenueMapDTO(show.getShowId(), 10, 10, 50.0), token);

        // Assert - the rebuilt layout reuses none of the previous build's zone or seat ids
        assertTrue(java.util.Collections.disjoint(zoneIdsOf(first), zoneIdsOf(second)),
                "zone ids must not be reused across builds");
        assertTrue(java.util.Collections.disjoint(seatIdsOf(first), seatIdsOf(second)),
                "seat ids must not be reused across builds");
    }

    @Test
    void givenTwoShows_whenEachDefinesLayout_thenZoneAndSeatIdsAreDisjointAcrossShows() {
        // Arrange
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show showA = storeShow(event.getEventId(), true, 100);
        Show showB = storeShow(event.getEventId(), true, 100);

        // Act
        VenueMapDTO mapA = showService.defineHallLayout(showA.getShowId(),
                createSeatedVenueMapDTO(showA.getShowId(), 10, 10, 50.0), token);
        VenueMapDTO mapB = showService.defineHallLayout(showB.getShowId(),
                createSeatedVenueMapDTO(showB.getShowId(), 10, 10, 50.0), token);

        // Assert - different shows never share zone or seat ids
        assertTrue(java.util.Collections.disjoint(zoneIdsOf(mapA), zoneIdsOf(mapB)),
                "zone ids must be unique across shows");
        assertTrue(java.util.Collections.disjoint(seatIdsOf(mapA), seatIdsOf(mapB)),
                "seat ids must be unique across shows");
    }

    @Test
    void givenDefinedLayout_whenUpdated_thenReloadedMapStillHasItsSeats() {
        // Arrange - reproduces the buyer-visible symptom at the service layer: after an edit
        // the reloaded zone must still expose its seats (otherwise the buyer view loses the grid).
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 100);
        showService.defineHallLayout(show.getShowId(),
                createSeatedVenueMapDTO(show.getShowId(), 10, 10, 50.0), token);

        // Act
        showService.updateHallLayout(show.getShowId(),
                createSeatedVenueMapDTO(show.getShowId(), 10, 10, 75.0), token);
        VenueMapDTO reloaded = showService.getHallLayout(show.getShowId());

        // Assert
        assertEquals(1, reloaded.zones.size());
        assertEquals(100, reloaded.zones.get(0).seats.size());
    }

    private static List<String> zoneIdsOf(VenueMapDTO map) {
        return map.zones.stream().map(z -> z.zoneId).toList();
    }

    private static List<String> seatIdsOf(VenueMapDTO map) {
        return map.zones.stream().flatMap(z -> z.seats.stream()).map(s -> s.seatId).toList();
    }

    ///////// Show capacity stays in sync with the venue map /////////

    @Test
    void givenStaleShowCapacity_whenDefineHallLayout_thenShowCapacityMatchesMap() {
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 999); // arbitrary/stale initial capacity

        showService.defineHallLayout(show.getShowId(),
                createSeatedVenueMapDTO(show.getShowId(), 10, 10, 50.0), token); // 100-seat map

        assertEquals(100, showRepo.findById(show.getShowId()).getTotalCapacity());
    }

    @Test
    void whenUpdateHallLayout_thenShowCapacityMatchesNewMap() {
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 100);
        showService.defineHallLayout(show.getShowId(),
                createSeatedVenueMapDTO(show.getShowId(), 10, 10, 50.0), token); // 100

        showService.updateHallLayout(show.getShowId(),
                createSeatedVenueMapDTO(show.getShowId(), 5, 10, 50.0), token); // 50

        assertEquals(50, showRepo.findById(show.getShowId()).getTotalCapacity());
    }

    @Test
    void whenRemoveHallLayout_thenShowCapacityIsZeroed() {
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 100);
        showService.defineHallLayout(show.getShowId(),
                createSeatedVenueMapDTO(show.getShowId(), 10, 10, 50.0), token);

        showService.removeHallLayout(show.getShowId(), token);

        assertEquals(0, showRepo.findById(show.getShowId()).getTotalCapacity());
    }

    @Test
    void givenSoldSeats_whenUpdateHallLayoutRejected_thenShowCapacityUnchanged() {
        // Reproduces the reported bug: a rejected layout edit must not move the auto-derived capacity.
        String token = storeMemberAndLogin(VALID_USERNAME, VALID_EMAIL, VALID_PASSWORD);
        ProductionCompany company = storeCompany(VALID_COMPANY_NAME, VALID_EMAIL_2);
        storeManagerInCompanyWithPermissions(company.getName(), company.getFounderID(), VALID_EMAIL, EVENT_EDITING_PERMISSIONS);

        Event event = storeEvent(VALID_COMPANY_NAME, true);
        Show show = storeShow(event.getEventId(), true, 70); // current (correct) capacity
        showRepo.saveVenueMapLayout(show.getShowId(), occupiedSeatedMap(show.getShowId(), 7, 10, 1)); // 70-seat map, 1 sold

        assertThrows(InvalidShowException.class, () -> showService.updateHallLayout(show.getShowId(),
                createSeatedVenueMapDTO(show.getShowId(), 10, 10, 50.0), token)); // tries to grow to 100

        assertEquals(70, showRepo.findById(show.getShowId()).getTotalCapacity()); // unchanged
    }

    // ─── Helpers for occupied venue maps ─── //

    /** Builds a seated venue map of rows x seatsPerRow, with the first {@code takenCount} seats marked taken. */
    private VenueMap occupiedSeatedMap(String showId, int rows, int seatsPerRow, int takenCount) {
        Zone zone = Zone.builder().zoneId("1").showId(showId).standing(false)
                .rows(rows).seatsPerRow(seatsPerRow).totalCapacity(rows * seatsPerRow).build();
        int taken = 0;
        for (int r = 1; r <= rows; r++) {
            for (int s = 1; s <= seatsPerRow; s++) {
                boolean isTaken = taken < takenCount;
                if (isTaken) taken++;
                zone.addSeat(Seat.builder().id("r" + r + "s" + s).row(r).number(s)
                        .price(50.0).isTaken(isTaken).build());
            }
        }
        return VenueMap.builder().showId(showId).mapName("Existing Hall")
                .totalCapacity(rows * seatsPerRow).zoneCount(1).zones(List.of(zone)).build();
    }

    /** Builds a standing venue map with the given capacity and currently-available count (capacity - available = reserved). */
    private VenueMap occupiedStandingMap(String showId, int capacity, int available) {
        Zone zone = Zone.builder().zoneId("1").showId(showId).standing(true)
                .standingCapacity(capacity).standingAvailable(available).totalCapacity(capacity).build();
        return VenueMap.builder().showId(showId).mapName("Standing Hall")
                .totalCapacity(capacity).zoneCount(1).zones(List.of(zone)).build();
    }

}
