package unit.domain.Aggregates.member;

import domain.Aggregates.member.Member;
import domain.exceptions.MemberException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemberSuspensionTest {

    private Member createMember() { return new Member("testUser", "test@mail.com", "hash"); }

    // Baseline / regression
    @Test
    void givenFreshMember_thenIsSuspendedIsFalse() {
        Member member = createMember();
        assertFalse(member.isSuspended());
    }

    @Test
    void givenFreshMember_thenSuspensionHistoryIsEmpty() {
        Member member = createMember();
        assertTrue(member.getSuspensionHistory().isEmpty());
    }

    // suspend()
    @Test
    void givenActiveMember_whenSuspend_thenIsSuspendedIsTrue() {
        Member member = createMember();
        member.suspend();
        assertTrue(member.isSuspended());
    }

    @Test
    void givenActiveMember_whenSuspend_thenSuspensionHistoryHasOneEntry() {
        Member member = createMember();
        member.suspend();
        assertEquals(1, member.getSuspensionHistory().size());
    }

    @Test
    void givenActiveMember_whenSuspend_thenSuspensionEntryIsActive() {
        Member member = createMember();
        member.suspend();
        assertTrue(member.getSuspensionHistory().get(0).isActive());
    }

    @Test
    void givenActiveMember_whenSuspend_thenSuspensionDateIsRecorded() {
        Member member = createMember();
        member.suspend();
        assertNotNull(member.getSuspensionHistory().get(0).getSuspensionDate());
    }

    @Test
    void givenAlreadySuspendedMember_whenSuspendAgain_thenThrowsMemberException() {
        Member member = createMember();
        member.suspend();
        assertThrows(MemberException.class, () -> member.suspend());
    }

    @Test
    void givenAlreadySuspendedMember_whenSuspendAgain_thenHistorySizeDoesNotGrow() {
        Member member = createMember();
        member.suspend();
        try { member.suspend(); } catch (MemberException ignored) {}
        assertEquals(1, member.getSuspensionHistory().size());
    }

    // unsuspend()
    @Test
    void givenSuspendedMember_whenUnsuspend_thenIsSuspendedIsFalse() {
        Member member = createMember();
        member.suspend();
        member.unsuspend();
        assertFalse(member.isSuspended());
    }

    @Test
    void givenSuspendedMember_whenUnsuspend_thenSuspensionEntryIsClosed() {
        Member member = createMember();
        member.suspend();
        member.unsuspend();
        assertFalse(member.getSuspensionHistory().get(0).isActive());
    }

    @Test
    void givenSuspendedMember_whenUnsuspend_thenSuspensionEndDateIsSet() {
        Member member = createMember();
        member.suspend();
        member.unsuspend();
        assertNotNull(member.getSuspensionHistory().get(0).getEndDate());
    }

    @Test
    void givenSuspendedMember_whenUnsuspend_thenHistorySizeUnchanged() {
        Member member = createMember();
        member.suspend();
        member.unsuspend();
        assertEquals(1, member.getSuspensionHistory().size());
    }

    @Test
    void givenNotSuspendedMember_whenUnsuspend_thenThrowsMemberException() {
        Member member = createMember();
        assertThrows(MemberException.class, () -> member.unsuspend());
    }

    @Test
    void givenUnsuspendedMember_whenUnsuspendAgain_thenThrowsMemberException() {
        Member member = createMember();
        member.suspend();
        member.unsuspend();
        assertThrows(MemberException.class, () -> member.unsuspend());
    }

    // Lifecycle
    @Test
    void givenMember_whenSuspendUnsuspendSuspendAgain_thenHistoryHasTwoEntries() {
        Member member = createMember();
        member.suspend();
        member.unsuspend();
        member.suspend();
        assertEquals(2, member.getSuspensionHistory().size());
    }

    @Test
    void givenMember_whenSuspendUnsuspendSuspendAgain_thenOnlyLatestEntryIsActive() {
        Member member = createMember();
        member.suspend();
        member.unsuspend();
        member.suspend();
        assertFalse(member.getSuspensionHistory().get(0).isActive());
        assertTrue(member.getSuspensionHistory().get(1).isActive());
    }

    @Test
    void givenMember_whenSuspendUnsuspendSuspendAgain_thenIsSuspendedIsTrue() {
        Member member = createMember();
        member.suspend();
        member.unsuspend();
        member.suspend();
        assertTrue(member.isSuspended());
    }

    @Test
    void givenSuspendedMember_whenSuspendAndUnsuspend_thenIsActiveUnchanged() {
        Member member = createMember();
        member.suspend();
        member.unsuspend();
        assertTrue(member.isActive());
    }
}