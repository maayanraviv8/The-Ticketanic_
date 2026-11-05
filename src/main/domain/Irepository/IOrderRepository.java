package domain.Irepository;

import domain.Aggregates.order.Order;

import java.time.LocalDateTime;
import java.util.List;

public interface IOrderRepository extends IRepository<Order> {

    List<Order> getAll();
    List<Order> findByEventId(String eventId);
    List<Order> findByEventIdAndDateRange(String eventId, LocalDateTime from, LocalDateTime to);
}
