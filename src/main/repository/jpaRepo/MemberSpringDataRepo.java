package repository.jpaRepo;

import domain.Aggregates.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberSpringDataRepo extends JpaRepository<Member, String> {

    Optional<Member> findByEmail(String email);

    Optional<Member> findByUsername(String username);

    Optional<Member> findByActiveOrderId(String activeOrderId);
}