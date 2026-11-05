package repository.memoryRepo;

import domain.Aggregates.member.Member;
import domain.Irepository.IMemberRepository;
import domain.exceptions.OptimisticLockingFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Profile({"dev", "test"})
@Repository
public class MemberMemoryRepo implements IMemberRepository {

    private static final Logger logger = LoggerFactory.getLogger(MemberMemoryRepo.class);
    private final ConcurrentHashMap<String, Member> members = new ConcurrentHashMap<>();

    @Override
    public void store(Member entity) {
        String key = entity.getEmail();
        if (key == null) {
            throw new IllegalArgumentException("Member email cannot be null");
        }

        if (entity.getVersion() == 0) {
            // Atomic check-and-insert for new members
            Member existing = members.putIfAbsent(key, entity);
            if (existing != null) {
                logger.warn("Member with email {} already exists", key);
                throw new OptimisticLockingFailureException("Member already exists");
            }
            logger.info("Created new member with email: {}, version: 0", key);
            entity.setVersion(1);
        } else {
            // Synchronized update for existing members
            synchronized (members) {
                Member existing = members.get(key);
                if (existing == null) {
                    throw new OptimisticLockingFailureException("Member not found for update: " + key);
                }
                if (existing.getVersion() != entity.getVersion()) {
                    logger.error("Optimistic locking failure for member email: {}. Expected version: {}, Actual version: {}", 
                                 key, entity.getVersion(), existing.getVersion());
                    throw new OptimisticLockingFailureException("Optimistic locking failure");
                }
                logger.info("Updating member with email: {}, version: {}", key, entity.getVersion());
                entity.setVersion(entity.getVersion() + 1);
                members.put(key, entity);
            }
        }
    }

    @Override
    public void delete(String email) {
        logger.info("Deleting member with email: {}", email);
        members.remove(email);
    }

    @Override
    public Member findById(String email) {
        return members.get(email);
    }

    @Override
    public List<Member> getAll() {
        return new ArrayList<>(members.values());
    }

    @Override
    public Member findByEmail(String email) {
        return findById(email);
    }

    @Override
    public Member findByUsername(String username) {
        return members.values().stream()
                .filter(m -> m.getUsername().equals(username))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Member findByActiveOrderId(String activeOrderId) {
        return members.values().stream()
                .filter(m -> activeOrderId.equals(m.getActiveOrderId()))
                .findFirst()
                .orElse(null);
    }
}
