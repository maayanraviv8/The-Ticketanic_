package repository.memoryRepo;

import domain.Aggregates.ticket.Ticket;
import domain.Irepository.ITicketRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Profile({"dev", "test"})
@Repository
public class TicketMemoryRepo implements ITicketRepository {

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    @Override
    public void store(Ticket entity) {
        if (entity.getId() == null) {
            throw new IllegalArgumentException("Ticket ID cannot be null");
        }
        tickets.put(entity.getId(), entity);
    }

    @Override
    public void delete(String id) {
        tickets.remove(id);
    }

    @Override
    public Ticket findById(String id) {
        return tickets.get(id);
    }

    @Override
    public List<Ticket> getAll() {
        return new ArrayList<>(tickets.values());
    }
}
