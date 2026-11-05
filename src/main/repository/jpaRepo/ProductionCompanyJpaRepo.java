package repository.jpaRepo;

import domain.Aggregates.productioncompany.ProductionCompany;
import domain.Irepository.IProductionCompanyRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Repository
@Profile("cloud")
public class ProductionCompanyJpaRepo implements IProductionCompanyRepository {
    private static final Logger logger = LoggerFactory.getLogger(ProductionCompanyJpaRepo.class);

    private final ProductionCompanySpringDataRepo springRepo;

    public ProductionCompanyJpaRepo(ProductionCompanySpringDataRepo springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public void store(ProductionCompany entity) {
        String name = entity.getName();
        boolean isNew = entity.getVersion() == 0;

        if (isNew) {
            try {
                springRepo.saveAndFlush(entity);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                logger.warn("Production company '{}' already exists", name);
                throw new domain.exceptions.OptimisticLockException("Production company already exists");
            }
            logger.info("Stored new production company '{}', version: 0", name);
            return;
        }

        if (!springRepo.existsById(name)) {
            throw new domain.exceptions.OptimisticLockException("Production company not found for update: " + name);
        }

        try {
            logger.info("Updating production company '{}', version: {}", name, entity.getVersion());
            springRepo.saveAndFlush(entity);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            logger.error("Optimistic locking failure for company: {}. Expected version: {}", name, entity.getVersion());
            throw new domain.exceptions.OptimisticLockException("Optimistic locking failure");
        } catch (jakarta.persistence.OptimisticLockException e) {
            logger.error("Optimistic locking failure for company: {}. Expected version: {}", name, entity.getVersion());
            throw new domain.exceptions.OptimisticLockException("Optimistic locking failure");
        }
    }
    

    @Override
    public void delete(String id) {
        if (!springRepo.existsById(id))
            throw new IllegalArgumentException("Production company not found: " + id);
        springRepo.deleteById(id);
        logger.info("Deleted production company '{}'", id);
    }

    @Override
    public ProductionCompany findById(String id) {
        return springRepo.findById(id).orElse(null);
    }

    @Override
    public List<ProductionCompany> getAll() {
        return springRepo.findAll();
    }

    @Override
    public boolean isActive(String productionCompanyId) {
        return springRepo.findById(productionCompanyId)
                .map(ProductionCompany::isActive)
                .orElse(false);
    }
}
