package repository.memoryRepo;

import domain.Aggregates.order.Order;
import domain.Irepository.IOrderRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Profile({"dev", "test"})
@Repository
public class OrderMemoryRepo implements IOrderRepository {

    private final Map<String, Order> store = new HashMap<>();

    @Override
    public void store(Order entity) {
        store.put(entity.getOrderId(), entity);
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }

    @Override
    public Order findById(String id) {
        return store.get(id);
    }

    @Override
    public List<Order> getAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<Order> findByEventId(String eventId) {
        return store.values().stream()
                .filter(o -> eventId.equals(o.getEventId()))
                .toList();
    }

    @Override
    public List<Order> findByEventIdAndDateRange(String eventId, LocalDateTime from, LocalDateTime to) {
        return store.values().stream()
                .filter(o -> eventId.equals(o.getEventId()))
                .filter(o -> o.getPurchaseTime() != null
                        && (from == null || !o.getPurchaseTime().isBefore(from))
                        && (to == null || !o.getPurchaseTime().isAfter(to)))
                .toList();
    }

}

