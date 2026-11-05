package repository.jpaRepo;

import domain.Aggregates.show.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ShowSpringDataRepo extends JpaRepository<Show, String> {

    List<Show> findByEventId(String eventId);

    @Query("SELECT s FROM Show s WHERE s.date >= :startDate AND s.date <= :endDate")
    List<Show> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT s FROM Show s WHERE s.eventId IN " +
       "(SELECT e.eventId FROM Event e WHERE e.productionCompanyName = :pc)")
    List<Show> findByProductionCompanyName(@Param("pc") String productionCompanyName);

    boolean existsByEventIdAndDate(String eventId, LocalDateTime showDate);

    List<Show> findByEventIdOrderByDate(String eventId);

    int countByEventId(String eventId);
}
