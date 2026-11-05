package domain.Irepository;

import domain.Aggregates.productioncompany.ProductionCompany;
import domain.Irepository.IRepository;
import java.util.List;
import java.time.LocalDateTime;

/**
 * Repository interface for ProductionCompany aggregate.
 * Supports company retrieval and sales-report queries.
 */
public interface IProductionCompanyRepository extends IRepository<ProductionCompany> {
    /**
     * Returns whether the production company with the given ID exists and is active.
     *
     * @param productionCompanyId - The production company ID
     * @return true if the company exists and its active flag is set
     */
    boolean isActive(String productionCompanyId);
}
