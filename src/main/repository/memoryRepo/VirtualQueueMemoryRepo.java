package repository.memoryRepo;

import domain.Aggregates.virtualqueue.VirtualQueue;
import domain.Irepository.IVirtualQueueRepository;
import infra.config.QueueProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Profile({"dev", "test", "cloud"})
@Repository
public class VirtualQueueMemoryRepo implements IVirtualQueueRepository {

    private final ConcurrentHashMap<String, VirtualQueue> queues = new ConcurrentHashMap<>();
    private final QueueProperties queueProperties;

    public VirtualQueueMemoryRepo(QueueProperties queueProperties) {
        this.queueProperties = queueProperties;
    }

    @Override
    public void store(VirtualQueue entity) {
        if (entity == null || entity.getShowId() == null) {
            throw new IllegalArgumentException("VirtualQueue or its showId cannot be null");
        }
        queues.put(entity.getShowId(), entity);
    }

    @Override
    public void delete(String id) {
        queues.remove(id);
    }

    @Override
    public VirtualQueue findById(String id) {
        return queues.computeIfAbsent(id, showId -> new VirtualQueue(
                showId,
                queueProperties.maxConcurrentUsers(),
                queueProperties.browsingSessionMinutes()));
    }

    @Override
    public List<VirtualQueue> getAll() {
        return new ArrayList<>(queues.values());
    }
}
