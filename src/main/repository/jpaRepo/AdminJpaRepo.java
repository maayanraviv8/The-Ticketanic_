package repository.jpaRepo;

import domain.Aggregates.admin.Admin;
import domain.Irepository.IAdminRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Repository
@Profile("cloud")
public class AdminJpaRepo implements IAdminRepository {
    private static final Logger logger = LoggerFactory.getLogger(AdminJpaRepo.class);

    private final AdminSpringDataRepo springRepo;

    public AdminJpaRepo(AdminSpringDataRepo springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public void store(Admin entity) {
        boolean isNew = !springRepo.existsById(entity.getEmail());
        try {
            springRepo.save(entity);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            logger.warn("Optimistic locking conflict on admin '{}'", entity.getEmail());
            throw new domain.exceptions.OptimisticLockException(
                    "Optimistic locking failure for admin '" + entity.getEmail() + "'");
        } catch (jakarta.persistence.OptimisticLockException e) {
            logger.warn("Optimistic locking conflict on admin '{}'", entity.getEmail());
            throw new domain.exceptions.OptimisticLockException(
                    "Optimistic locking failure for admin '" + entity.getEmail() + "'");
        }

        if (isNew) {
            logger.info("Admin '{}' created.", entity.getEmail());
        } else {
            logger.info("Admin '{}' updated.", entity.getEmail());
        }
    }

    @Override
    public void delete(String id) {
        if (!springRepo.existsById(id))
            throw new IllegalArgumentException("Admin not found or already deleted.");
        springRepo.deleteById(id);
        logger.info("Admin '{}' deleted.", id);
    }

    @Override
    public Admin findById(String id) {
        return springRepo.findById(id).orElse(null);
    }

    @Override
    public List<Admin> getAll() {
        return springRepo.findAll();
    }
}