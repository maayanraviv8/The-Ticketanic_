package domain.Irepository;

import domain.Aggregates.show.Show;
import domain.Aggregates.show.VenueMap;
import java.util.List;
import java.time.LocalDateTime;

/**
 * Repository interface for Show aggregate (UC14 - Manage Event Inventory)
 * Supports show retrieval, inventory management, and availability tracking.
 */
public interface IShowRepository extends IRepository<Show> {

    /**
     * Finds all shows for a specific event.
     * 
     * @param eventId - The event ID
     * @return List of shows for the event
     */
    List<Show> findByEventId(String eventId);

    /**
     * Finds shows within a specific date range.
     * 
     * @param startDate - Start date for the search
     * @param endDate   - End date for the search
     * @return List of shows within the date range
     */
    List<Show> findByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Checks if a show with a specific date already exists for an event.
     * 
     * @param eventId  - The event ID
     * @param showDate - The show date to check
     * @return boolean - True if show exists for that date
     */
    boolean existsByEventIdAndDate(String eventId, LocalDateTime showDate);

    /**
     * Finds all shows for an event sorted by date.
     * 
     * @param eventId - The event ID
     * @return List of shows sorted chronologically
     */
    List<Show> findByEventIdOrderByDate(String eventId);

    /**
     * Gets the total available inventory (seats + standing capacity) for a show.
     * 
     * @param showId - The show ID
     * @return int - Total inventory count
     */
    int getTotalInventoryByShowId(String showId);

    /**
     * Updates a show's zone configuration and capacity.
     * 
     * @param showId      - The show ID
     * @param updatedShow - The show with updated zones
     * @return boolean - True if update was successful
     */
    boolean updateShowZones(String showId, Show updatedShow);

    /**
     * Counts total shows for a specific event.
     * 
     * @param eventId - The event ID
     * @return int - Number of shows
     */
    int countByEventId(String eventId);

    // ============ UC15 - Define Hall Layout in Event Map Methods ============

    /**
     * Saves a venue map layout for a show.
     * 
     * @param showId       - The show ID
     * @param venueMapData - The venue map configuration to save
     * @return boolean - True if save was successful
     */
    boolean saveVenueMapLayout(String showId, Object venueMapData);

    /**
     * Retrieves the venue map for a specific show.
     * 
     * @param showId - The show ID
     * @return Object - The venue map configuration, or null if not found
     */
    VenueMap getVenueMapByShowId(String showId);

    /**
     * Validates that the venue map capacity matches the show's inventory capacity.
     * Critical for UC15: Prevents discrepancies between map and inventory.
     * 
     * @param showId       - The show ID
     * @param venueMapData - The venue map configuration to validate
     * @return boolean - True if capacities match exactly
     */
    boolean validateMapCapacityMatch(String showId, Object venueMapData);

    /**
     * Updates the venue map layout for a show.
     * 
     * @param showId       - The show ID
     * @param venueMapData - The updated venue map configuration
     * @return boolean - True if update was successful
     */
    boolean updateVenueMapLayout(String showId, Object venueMapData);

    /**
     * Deletes the venue map for a show.
     * 
     * @param showId - The show ID
     * @return boolean - True if deletion was successful
     */
    boolean deleteVenueMap(String showId);

    /**
     * Checks if a venue map exists for a show.
     * 
     * @param showId - The show ID
     * @return boolean - True if venue map exists
     */
    boolean venueMapExists(String showId);

    /**
     * Gets the total capacity defined in the venue map.
     * 
     * @param showId - The show ID
     * @return int - Total capacity in the venue map, or 0 if no map exists
     */
    int getTotalMapCapacity(String showId);
}
