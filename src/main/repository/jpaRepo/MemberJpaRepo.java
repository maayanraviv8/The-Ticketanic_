package repository.jpaRepo;

import domain.Aggregates.member.Member;
import domain.Irepository.IMemberRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Repository
@Profile("cloud")
public class MemberJpaRepo implements IMemberRepository {
    private static final Logger logger = LoggerFactory.getLogger(MemberJpaRepo.class);

    private final MemberSpringDataRepo springRepo;

    public MemberJpaRepo(MemberSpringDataRepo springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public void store(Member entity) {
        String key = entity.getEmail();
        if (key == null) {
            throw new IllegalArgumentException("Member email cannot be null");
        }

        if (entity.getVersion() == 0) {
            try {
                springRepo.saveAndFlush(entity);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                logger.warn("Member with email {} already exists", key);
                throw new domain.exceptions.OptimisticLockingFailureException("Member already exists");
            }
            logger.info("Created new member with email: {}, version: 0", key);
            return;
        }

        if (!springRepo.existsById(key)) {
            throw new domain.exceptions.OptimisticLockingFailureException("Member not found for update: " + key);
        }

        try {
            logger.info("Updating member with email: {}, version: {}", key, entity.getVersion());
            springRepo.saveAndFlush(entity);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            logger.error("Optimistic locking failure for member email: {}. Expected version: {}", key, entity.getVersion());
            throw new domain.exceptions.OptimisticLockingFailureException("Optimistic locking failure");
        }
    }

    @Override
    public void delete(String id) {
        logger.info("Deleting member with email: {}", id);
        if (springRepo.existsById(id))
            springRepo.deleteById(id);
    }

    @Override
    public Member findById(String id) {
        return springRepo.findById(id).orElse(null);
    }

    @Override
    public List<Member> getAll() {
        return springRepo.findAll();
    }

    @Override
    public Member findByEmail(String email) {
        return springRepo.findById(email).orElse(null);
    }

    @Override
    public Member findByUsername(String username) {
        return springRepo.findByUsername(username).orElse(null);
    }

    @Override
    public Member findByActiveOrderId(String activeOrderId) {
        return springRepo.findByActiveOrderId(activeOrderId).orElse(null);
    }
}