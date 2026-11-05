package repository.jpaRepo;

import domain.Aggregates.event.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EventSpringDataRepo extends JpaRepository<Event, String>,JpaSpecificationExecutor<Event> {

    List<Event> findByProductionCompanyName(String productionCompanyName);

    List<Event> findByEventType_TypeName(String eventType);

    boolean existsByNameAndProductionCompanyName(String eventName, String productionCompanyName);

    @Query("SELECT e FROM Event e WHERE e.startDate >= :startDate AND e.endDate <= :endDate")
    List<Event> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT e FROM Event e WHERE e.raffle IS NOT NULL")
    List<Event> findRaffleEnabledEvents();

    int countByProductionCompanyName(String productionCompanyName);

    List<Event> findByManagerId(String managerId);
}
