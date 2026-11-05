package repository.jpaRepo;

import domain.Aggregates.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderSpringDataRepo extends JpaRepository<Order, String> {

    List<Order> findByEventId(String eventId);

    @Query("SELECT o FROM Order o WHERE o.eventId = :eventId" +
       " AND (:from IS NULL OR o.purchaseTime >= :from)" +
       " AND (:to   IS NULL OR o.purchaseTime <= :to)")
    List<Order> findByEventIdAndDateRange(
            @Param("eventId") String eventId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}