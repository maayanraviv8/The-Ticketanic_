package repository.jpaRepo;

import domain.Aggregates.activeorder.ActiveOrder;
import domain.Irepository.IActiveOrderRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Profile("cloud")
public class ActiveOrderJpaRepo implements IActiveOrderRepository {

    private final ActiveOrderSpringDataRepo springRepo;

    public ActiveOrderJpaRepo(ActiveOrderSpringDataRepo springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public void store(ActiveOrder entity) {
        if (entity.getId() == null) {
            throw new IllegalArgumentException("ActiveOrder ID cannot be null");
        }
        springRepo.save(entity);
    }

    @Override
    public void delete(String id) {
        if (springRepo.existsById(id))
            springRepo.deleteById(id);
    }

    @Override
    public ActiveOrder findById(String id) {
        return springRepo.findById(id).orElse(null);
    }

    @Override
    public List<ActiveOrder> getAll() {
        return springRepo.findAll();
    }
}