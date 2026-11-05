package repository.jpaRepo;

import domain.Aggregates.ticket.Ticket;
import domain.Irepository.ITicketRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Profile("cloud")
public class TicketJpaRepo implements ITicketRepository {

    private final TicketSpringDataRepo springRepo;

    public TicketJpaRepo(TicketSpringDataRepo springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public void store(Ticket entity) {
        if (entity.getId() == null) {
            throw new IllegalArgumentException("Ticket ID cannot be null");
        }
        springRepo.save(entity);
    }

    @Override
    public void delete(String id) {
        if (springRepo.existsById(id))
            springRepo.deleteById(id);
    }

    @Override
    public Ticket findById(String id) {
        return springRepo.findById(id).orElse(null);
    }

    @Override
    public List<Ticket> getAll() {
        return springRepo.findAll();
    }
}