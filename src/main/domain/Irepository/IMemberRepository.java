package domain.Irepository;

import domain.Aggregates.member.Member;

public interface IMemberRepository extends IRepository<Member> {
    Member findByEmail(String email);
    Member findByUsername(String username);
    Member findByActiveOrderId(String activeOrderId);
}
