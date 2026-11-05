package repository.jpaRepo;

import domain.Aggregates.activeorder.ActiveOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActiveOrderSpringDataRepo extends JpaRepository<ActiveOrder, String> {

}
