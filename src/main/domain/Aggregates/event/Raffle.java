package domain.Aggregates.event;

import domain.exceptions.RaffleException;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

/**
 * Raffle / purchase-right lottery attached to an {@link Event} (requirement II.3.9).
 * <p>
 * A raffle is OPEN for registration until {@link #registrationDeadline}, which doubles as
 * the sale-open moment: once it passes, registration is closed and the next access triggers
 * the draw (see {@link #isDrawDue}). The draw selects winners up to the configured
 * {@link #numberOfWinners} (independent of event capacity) and mints each a
 * unique, time-limited {@link RaffleAuthorization}. Only a winner holding a valid code may
 * enter the reservation phase.
 * <p>
 * All raffle invariants are enforced here, in the aggregate.
 */
@Entity
@Table(name = "raffles")
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Raffle {

    @Id
    @Column(name = "raffle_id")
    private String raffleId;

    @Column(name = "event_id")
    private String eventId;

    /** Registration cutoff; also the sale-open moment that makes the draw due. */
    @Column(name = "registration_deadline")
    private LocalDateTime registrationDeadline;

    /** How long a winner's code stays valid once drawn. */
    @Builder.Default
    @Column(name = "code_validity_minutes")
    private Duration codeValidity = Duration.ofMinutes(30);

    /**
     * How many winners the draw awards (the number of purchase rights granted), independent of the
     * event's total capacity. The draw selects at most this many at random; fewer if there are fewer
     * participants. Set at configuration time and validated against capacity by {@link Event}.
     */
    @Column(name = "number_of_winners")
    private int numberOfWinners;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private RaffleStatus status = RaffleStatus.OPEN;

    /** Registered member emails (insertion order preserved for deterministic behavior). */
    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "raffle_participants", joinColumns = @JoinColumn(name = "raffle_id"))
    @Column(name = "participant_email")
    private Set<String> participants = new LinkedHashSet<>();

    /** Winner email -> their authorization. Populated by {@link #drawWinners}. */
    @Builder.Default
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true, mappedBy = "raffle")
    @MapKey(name = "winnerEmail")
    private Map<String, RaffleAuthorization> winners = new HashMap<>();

    /**
     * Keeps {@link RaffleAuthorization#getRaffle()} in sync with this map before every
     * insert/update. {@code raffle_id} is owned solely by that side now (see {@code mappedBy}
     * above), so any winner added without explicitly setting the back-reference (e.g. via
     * {@link #drawWinners}, or test/seed fixtures building the map directly) still persists
     * correctly.
     */
    @PrePersist
    @PreUpdate
    private void linkWinnersToRaffle() {
        for (RaffleAuthorization authorization : winners.values()) {
            authorization.setRaffle(this);
        }
    }

    /** Register a member before the deadline. Throws if closed, past deadline, or duplicate. */
    public void register(String memberEmail, LocalDateTime now) {
        if (status != RaffleStatus.OPEN)
            throw new RaffleException("Raffle registration is closed.");
        if (registrationDeadline != null && !now.isBefore(registrationDeadline))
            throw new RaffleException("Raffle registration has closed.");
        if (participants.contains(memberEmail))
            throw new RaffleException("You are already registered for this raffle.");
        participants.add(memberEmail);
    }

    /** True once the sale has opened (deadline passed) but winners have not yet been drawn. */
    public boolean isDrawDue(LocalDateTime now) {
        return status == RaffleStatus.OPEN
                && registrationDeadline != null
                && !now.isBefore(registrationDeadline);
    }

    /**
     * Draw winners using this raffle's configured {@link #numberOfWinners}. The production draw path;
     * the winner count is the configured allocation, not the event capacity.
     */
    public Set<String> drawWinners(Supplier<String> codeGenerator, LocalDateTime now) {
        return drawWinners(this.numberOfWinners, codeGenerator, now);
    }

    /**
     * Draw up to {@code maxWinners} winners at random, minting each a unique time-limited code.
     * Idempotent guard: throws if the raffle is not OPEN (already drawn). Returns the winner emails.
     */
    public Set<String> drawWinners(int maxWinners, Supplier<String> codeGenerator, LocalDateTime now) {
        if (status != RaffleStatus.OPEN)
            throw new RaffleException("Raffle winners have already been drawn.");

        List<String> pool = new ArrayList<>(participants);
        Collections.shuffle(pool);
        int winnerCount = Math.min(pool.size(), Math.max(maxWinners, 0));

        LocalDateTime expiresAt = now.plus(codeValidity);
        for (int i = 0; i < winnerCount; i++) {
            String email = pool.get(i);
            String code = generateUniqueCode(codeGenerator);
            RaffleAuthorization authorization = new RaffleAuthorization(code, email, expiresAt);
            authorization.setRaffle(this);
            winners.put(email, authorization);
        }
        status = RaffleStatus.DRAWN;
        return Collections.unmodifiableSet(new LinkedHashSet<>(winners.keySet()));
    }

    private String generateUniqueCode(Supplier<String> codeGenerator) {
        Set<String> used = new HashSet<>();
        for (RaffleAuthorization a : winners.values())
            used.add(a.getCode());
        String code;
        do {
            code = codeGenerator.get();
        } while (used.contains(code));
        return code;
    }

    /** A code is valid iff the raffle is drawn and the code is this member's, unexpired authorization. */
    public boolean validateCode(String code, String memberEmail, LocalDateTime now) {
        if (status != RaffleStatus.DRAWN || code == null)
            return false;
        RaffleAuthorization auth = winners.get(memberEmail);
        return auth != null && auth.getCode().equals(code) && auth.isValid(now);
    }

    public boolean isParticipant(String memberEmail) {
        return participants.contains(memberEmail);
    }

    public boolean isWinner(String memberEmail) {
        return winners.containsKey(memberEmail);
    }

    public RaffleAuthorization getAuthorization(String memberEmail) {
        return winners.get(memberEmail);
    }

}
