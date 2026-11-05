package repository.jpaRepo;

import domain.Aggregates.productioncompany.ProductionCompany;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionCompanySpringDataRepo extends JpaRepository<ProductionCompany, String> {

}
