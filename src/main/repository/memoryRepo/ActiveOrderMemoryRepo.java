package repository.memoryRepo;

import domain.Aggregates.activeorder.ActiveOrder;
import domain.Irepository.IActiveOrderRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Profile({"dev", "test"})
@Repository
public class ActiveOrderMemoryRepo implements IActiveOrderRepository {

    private final Map<String, ActiveOrder> activeOrders = new ConcurrentHashMap<>();

    @Override
    public void store(ActiveOrder entity) {
        if (entity.getId() == null) {
            throw new IllegalArgumentException("ActiveOrder ID cannot be null");
        }
        activeOrders.put(entity.getId(), entity);
    }

    @Override
    public void delete(String id) {
        activeOrders.remove(id);
    }

    @Override
    public ActiveOrder findById(String id) {
        return activeOrders.get(id);
    }

    @Override
    public List<ActiveOrder> getAll() {
        return new ArrayList<>(activeOrders.values());
    }
}
