package repository.memoryRepo;

import domain.Aggregates.admin.Admin;
import domain.Irepository.IAdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Profile({"dev", "test"})
@Repository
public class AdminMemoryRepo implements IAdminRepository {
    private static final Logger logger = LoggerFactory.getLogger(AdminMemoryRepo.class);

    private final ConcurrentHashMap<String, Admin> admins = new ConcurrentHashMap<>();

    @Override
    public synchronized void store(Admin entity) {
        Admin current = admins.get(entity.getEmail());
        if (current == null) {
            admins.put(entity.getEmail(), entity);
            logger.info("Admin '{}' created.", entity.getEmail());
        } else {
            admins.replace(entity.getEmail(), entity);
            logger.info("Admin '{}' updated.", entity.getEmail());
        }
    }

    @Override
    public synchronized void delete(String id) {
        if (!admins.containsKey(id))
            throw new IllegalArgumentException("Admin not found or already deleted.");
        admins.remove(id);
        logger.info("Admin '{}' deleted.", id);
    }

    @Override
    public Admin findById(String id) {
        return admins.get(id);
    }

    @Override
    public List<Admin> getAll() {
        return new ArrayList<>(admins.values());
    }
}