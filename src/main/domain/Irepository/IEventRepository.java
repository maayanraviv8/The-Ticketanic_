package domain.Irepository;

import domain.Aggregates.event.Event;
import java.util.List;
import java.time.LocalDateTime;

/**
 * Repository interface for Event aggregate (UC14 - Manage Event Inventory)
 * Supports event retrieval, validation, and inventory management operations.
 */
public interface IEventRepository extends IRepository<Event> {

    /**
     * Finds all events under a specific production company.
     * 
     * @param productionCompanyId - The production company ID
     * @return List of events for the company
     */
    List<Event> findByProductionCompanyId(String productionCompanyId);

    /**
     * Finds all events by their type.
     * 
     * @param eventType - The event type to filter by
     * @return List of events matching the type
     */
    List<Event> findByEventType(String eventType);

    /**
     * Checks if an event with a specific name already exists in a production
     * company.
     * 
     * @param eventName           - The event name
     * @param productionCompanyId - The production company ID
     * @return boolean - True if event exists
     */
    boolean existsByNameAndProductionCompany(String eventName, String productionCompanyId);

    /**
     * Finds events within a specific date range (for validation and filtering).
     * 
     * @param startDate - Start date for the search
     * @param endDate   - End date for the search
     * @return List of events within the date range
     */
    List<Event> findByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Finds all events that require raffle access (for UC14 inventory validation).
     * 
     * @return List of events with raffle enabled
     */
    List<Event> findRaffleEnabledEvents();

    /**
     * Updates an event's inventory details.
     * 
     * @param eventId      - The event ID
     * @param updatedEvent - The event with updated inventory
     * @return boolean - True if update was successful
     */
    boolean updateEventInventory(String eventId, Event updatedEvent);

    /**
     * Counts total events under a specific production company.
     * 
     * @param productionCompanyId - The production company ID
     * @return int - Number of events
     */
    int countByProductionCompanyId(String productionCompanyId);

    /**
     * Finds all events created by a specific manager.
     *
     * @param managerId - The manager's ID (email)
     * @return List of events created by that manager
     */
    List<Event> findByManagerId(String managerId);

    /**
     * Searches for active events matching the given criteria.
     * @param productionCompanyName - Filter by production company name 
     * @param keywords - Search in event name, artist
     * @param category - Filter by category 
     * @param startDate - Filter events starting from this date
     * @param endDate - Filter events ending before this date
     * @param location - Filter by location 
     * @param minPrice - Filter events with max price >= this value
     * @param maxPrice - Filter events with min price <= this value
     * @return List of matching active events
     */
    List<Event> searchEvents(String productionCompanyName, List<String> keywords,
            String category, LocalDateTime startDate, LocalDateTime endDate,
            String location, Double minPrice, Double maxPrice);
  
}
