package repository.jpaRepo;

import domain.Aggregates.order.Order;
import domain.Irepository.IOrderRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@Profile("cloud")
public class OrderJpaRepo implements IOrderRepository {

    private final OrderSpringDataRepo springRepo;

    public OrderJpaRepo(OrderSpringDataRepo springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public void store(Order entity) {
        springRepo.save(entity);
    }

    @Override
    public void delete(String id) {
        if (springRepo.existsById(id))
            springRepo.deleteById(id);
    }

    @Override
    public Order findById(String id) {
        return springRepo.findById(id).orElse(null);
    }

    @Override
    public List<Order> getAll() {
        return springRepo.findAll();
    }

    @Override
    public List<Order> findByEventId(String eventId) {
        return springRepo.findByEventId(eventId);
    }

    @Override
    public List<Order> findByEventIdAndDateRange(String eventId, LocalDateTime from, LocalDateTime to) {
        return springRepo.findByEventIdAndDateRange(eventId, from, to);
    }
}
