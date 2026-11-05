package repository.jpaRepo;

import domain.Aggregates.ticket.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketSpringDataRepo extends JpaRepository<Ticket, String> {

}
