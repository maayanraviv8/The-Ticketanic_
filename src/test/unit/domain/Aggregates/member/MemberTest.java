package unit.domain.Aggregates.member;

import domain.Aggregates.member.Member;
import domain.exceptions.AccountSuspendedException;
import domain.exceptions.MemberException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemberTest {

    private static final String USERNAME = "alice";
    private static final String EMAIL = "alice@example.com";
    private static final String HASHED_PASSWORD = "hashed";

    @Test
    void givenActiveMember_whenEnsureCanLogin_thenDoesNotThrow() {
        Member member = new Member(USERNAME, EMAIL, HASHED_PASSWORD);

        assertDoesNotThrow(member::ensureCanLogin);
    }

    @Test
    void givenInactiveMember_whenEnsureCanLogin_thenThrowsAccountCancelled() {
        Member member = new Member(USERNAME, EMAIL, HASHED_PASSWORD);
        member.cancelMembership();

        AccountSuspendedException ex = assertThrows(AccountSuspendedException.class, member::ensureCanLogin);
        assertEquals("This account has been cancelled", ex.getMessage());
    }

    @Test
    void givenSuspendedMember_whenEnsureCanLogin_thenThrowsAccountSuspended() {
        Member member = new Member(USERNAME, EMAIL, HASHED_PASSWORD);
        member.suspend();

        AccountSuspendedException ex = assertThrows(AccountSuspendedException.class, member::ensureCanLogin);
        assertEquals("This account has been suspended", ex.getMessage());
    }

    @Test
    void givenActiveMember_whenCancelMembership_thenIsInactive() {
        Member member = new Member(USERNAME, EMAIL, HASHED_PASSWORD);
        member.cancelMembership();
        assertFalse(member.isActive());
    }

    @Test
    void givenInactiveMember_whenCancelMembership_thenThrows() {
        Member member = new Member(USERNAME, EMAIL, HASHED_PASSWORD);
        member.cancelMembership();
        assertThrows(MemberException.class, member::cancelMembership);
    }

    @Test
    void whenRename_thenUsernameIsUpdated() {
        Member member = new Member(USERNAME, EMAIL, HASHED_PASSWORD);

        member.rename("bob");

        assertEquals("bob", member.getUsername());
    }

    @Test
    void whenChangePassword_thenHashedPasswordIsReplaced() {
        Member member = new Member(USERNAME, EMAIL, HASHED_PASSWORD);

        member.changePassword("newHash");

        assertEquals("newHash", member.getHashedPassword());
    }
}
