package repository.jpaRepo;

import domain.Aggregates.admin.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminSpringDataRepo extends JpaRepository<Admin, String> {

}
