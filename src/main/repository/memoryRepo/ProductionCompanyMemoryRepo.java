package repository.memoryRepo;

import domain.Aggregates.productioncompany.ProductionCompany;
import domain.Irepository.IProductionCompanyRepository;
import domain.exceptions.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Profile({"dev", "test"})
@Repository
public class ProductionCompanyMemoryRepo implements IProductionCompanyRepository {

    private static final Logger logger = LoggerFactory.getLogger(ProductionCompanyMemoryRepo.class);

    /**
     * Versioned wrapper for optimistic locking. ProductionCompany carries its own
     * version field; we also track it here so compute() can compare atomically.
     */
    private static final class Versioned {
        final ProductionCompany entity;
        final long version;

        Versioned(ProductionCompany entity, long version) {
            this.entity = entity;
            this.version = version;
        }
    }

    private final ConcurrentHashMap<String, Versioned> store = new ConcurrentHashMap<>();

    // ── IRepository ───────────────────────────────────────────────────────────

    @Override
    public void store(ProductionCompany entity) {
        String key = entity.getName();
        if (key == null) {
            throw new IllegalArgumentException("Production company name cannot be null");
        }

        if (entity.getVersion() == 0) {
            // Atomic check-and-insert for new companies
            Versioned existing = store.putIfAbsent(key, new Versioned(entity, 1));
            if (existing != null) {
                logger.warn("Production company '{}' already exists", key);
                throw new OptimisticLockException("Production company already exists");
            }
            logger.info("Stored new production company '{}', version: 0", key);
            entity.setVersion(1);
        } else {
            // Synchronized update for existing companies
            synchronized (store) {
                Versioned existing = store.get(key);
                if (existing == null) {
                    throw new OptimisticLockException("Production company not found for update: " + key);
                }
                if (existing.version != entity.getVersion()) {
                    logger.error("Optimistic locking failure for company: {}. Expected version: {}, Actual version: {}",
                            key, entity.getVersion(), existing.version);
                    throw new OptimisticLockException("Optimistic locking failure");
                }

                logger.info("Updating production company '{}', version: {}", key, entity.getVersion());
                entity.setVersion(entity.getVersion() + 1);
                store.put(key, new Versioned(entity, entity.getVersion()));
            }
        }
    }

    /**
     * Optimistic update — caller passes the entity with the version it read.
     * Uses ConcurrentHashMap.compute to compare-and-swap atomically.
     */
    public ProductionCompany update(String name, ProductionCompany updated) {
        final long expectedVersion = updated.getVersion();
        Versioned[] result = new Versioned[1];

        store.compute(name, (key, current) -> {
            if (current == null) {
                result[0] = null;
                return null;
            }
            if (current.version != expectedVersion) {
                logger.warn("Optimistic locking conflict on company '{}': expected={}, actual={}",
                        name, expectedVersion, current.version);
                result[0] = null;
                return current;
            }
            updated.setVersion(expectedVersion + 1);
            Versioned next = new Versioned(updated, expectedVersion + 1);
            result[0] = next;
            return next;
        });

        if (result[0] == null) {
            throw new OptimisticLockException(
                    "Concurrent modification detected for production company '" + name
                    + "' (expected version=" + expectedVersion + ")");
        }

        logger.info("Updated production company '{}' to version={}", name, result[0].version);
        return result[0].entity;
    }

    @Override
    public void delete(String name) {
        Versioned removed = store.remove(name);
        if (removed == null) {
            throw new IllegalArgumentException("Production company not found: " + name);
        }
        logger.info("Deleted production company '{}'", name);
    }

    @Override
    public ProductionCompany findById(String name) {
        Versioned v = store.get(name);
        if (v == null) return null;
        // Return a copy so the caller's mutations don't affect the stored instance.
        return new ProductionCompany(v.entity);
    }

    /** Returns the current version for a company — needed by callers before calling update(). */
    public long getVersion(String name) {
        Versioned v = store.get(name);
        if (v == null) throw new IllegalArgumentException("Production company not found: " + name);
        return v.version;
    }

    @Override
    public List<ProductionCompany> getAll() {
        List<ProductionCompany> result = new ArrayList<>();
        store.values().forEach(v -> result.add(new ProductionCompany(v.entity)));
        return result;
    }

    @Override
    public boolean isActive(String productionCompanyId) {
        Versioned v = store.get(productionCompanyId);
        return v != null && v.entity.isActive();
    }
}
