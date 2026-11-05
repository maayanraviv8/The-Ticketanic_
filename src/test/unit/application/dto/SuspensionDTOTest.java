package unit.application.dto;

import application.dto.SuspensionDTO;
import domain.Aggregates.member.Suspension;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SuspensionDTOTest {

    private Suspension activeSuspension() {
        return new Suspension("user@mail.com", LocalDateTime.now().minusHours(2));
    }

    private Suspension closedSuspension() {
        Suspension s = new Suspension("user@mail.com", LocalDateTime.now().minusHours(2));
        s.close(LocalDateTime.now().minusHours(1));
        return s;
    }

    // --- Active suspension (endDate == null) ---

    @Test
    void givenActiveSuspension_whenFrom_thenMemberEmailIsSet() {
        // Arrange
        Suspension s = activeSuspension();

        // Act
        SuspensionDTO dto = SuspensionDTO.from("user@mail.com", s);

        // Assert
        assertEquals("user@mail.com", dto.memberEmail);
    }

    @Test
    void givenActiveSuspension_whenFrom_thenSuspensionDateIsSet() {
        // Arrange
        Suspension s = activeSuspension();

        // Act
        SuspensionDTO dto = SuspensionDTO.from("user@mail.com", s);

        // Assert
        assertNotNull(dto.suspensionDate);
    }

    @Test
    void givenActiveSuspension_whenFrom_thenSuspensionEndDateIsNull() {
        // Arrange
        Suspension s = activeSuspension();

        // Act
        SuspensionDTO dto = SuspensionDTO.from("user@mail.com", s);

        // Assert
        assertNull(dto.suspensionEndDate);
    }

    @Test
    void givenActiveSuspension_whenFrom_thenDurationIsNotNull() {
        // Arrange
        Suspension s = activeSuspension();

        // Act
        SuspensionDTO dto = SuspensionDTO.from("user@mail.com", s);

        // Assert
        assertNotNull(dto.duration);
    }

    @Test
    void givenActiveSuspension_whenFrom_thenDurationIsNotNegative() {
        // Arrange
        Suspension s = activeSuspension();

        // Act
        SuspensionDTO dto = SuspensionDTO.from("user@mail.com", s);

        // Assert
        assertTrue(!dto.duration.isNegative());
    }

    @Test
    void givenActiveSuspension_whenFrom_thenDurationApproximatesTimeSinceSuspensionDate() {
        // Arrange
        LocalDateTime before = LocalDateTime.now();
        Suspension s = new Suspension("user@mail.com", before);

        // Act
        SuspensionDTO dto = SuspensionDTO.from("user@mail.com", s);

        // Assert
        assertTrue(dto.duration.toMinutes() >= 0);
        assertTrue(dto.duration.toHours() < 1);
    }

    // --- Closed suspension (endDate != null) ---

    @Test
    void givenClosedSuspension_whenFrom_thenSuspensionEndDateIsSet() {
        // Arrange
        Suspension s = closedSuspension();

        // Act
        SuspensionDTO dto = SuspensionDTO.from("user@mail.com", s);

        // Assert
        assertNotNull(dto.suspensionEndDate);
    }

    @Test
    void givenClosedSuspension_whenFrom_thenSuspensionEndDateMatchesSuspensionEndDate() {
        // Arrange
        Suspension s = new Suspension("user@mail.com", LocalDateTime.now().minusHours(3));
        LocalDateTime end = LocalDateTime.now().minusHours(1);
        s.close(end);

        // Act
        SuspensionDTO dto = SuspensionDTO.from("user@mail.com", s);

        // Assert
        assertEquals(end, dto.suspensionEndDate);
    }

    @Test
    void givenClosedSuspension_whenFrom_thenDurationMatchesBetweenStartAndEnd() {
        // Arrange
        LocalDateTime start = LocalDateTime.now().minusHours(3);
        LocalDateTime end = LocalDateTime.now().minusHours(1);
        Suspension s = new Suspension("user@mail.com", start);
        s.close(end);

        // Act
        SuspensionDTO dto = SuspensionDTO.from("user@mail.com", s);

        // Assert
        assertEquals(Duration.between(start, end), dto.duration);
    }

    @Test
    void givenClosedSuspension_whenFrom_thenDurationIsNotNegative() {
        // Arrange
        Suspension s = closedSuspension();

        // Act
        SuspensionDTO dto = SuspensionDTO.from("user@mail.com", s);

        // Assert
        assertTrue(!dto.duration.isNegative());
    }

    @Test
    void givenClosedSuspension_whenFrom_thenMemberEmailIsSet() {
        // Arrange
        Suspension s = closedSuspension();

        // Act
        SuspensionDTO dto = SuspensionDTO.from("user@mail.com", s);

        // Assert
        assertEquals("user@mail.com", dto.memberEmail);
    }
}