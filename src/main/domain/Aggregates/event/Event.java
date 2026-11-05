package domain.Aggregates.event;

import domain.Aggregates.order.CustomerDetails;
import domain.Aggregates.productioncompany.ProductionCompany;
import domain.exceptions.InvalidEventException;
import domain.exceptions.RaffleException;
import domain.policies.discount.IDiscountRule;
import domain.policies.purchase.IPurchaseRule;
import jakarta.persistence.*;

import lombok.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Event Aggregate - Represents a ticket event in the system
 */
@Entity
@Table(name = "events")
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(nullable = false)
    private String name;

    @Embedded
    private EventType eventType;

    @Column(name = "production_company_name")
    private String productionCompanyName;

    @Column(name = "manager_Id")
    private String managerId; // Member currently responsible for this event.

    private String artist;
    private String location;

    @Column(name = "min_price")
    private double minPrice;
    @Column(name = "max_price")
    private double maxPrice;

    @Column(name = "start_date")
    private LocalDateTime startDate;
    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "tickets_sold")
    @Builder.Default
    private int ticketsSold = 0;
    @Column(name = "total_revenue")
    @Builder.Default
    private double totalRevenue = 0.0;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "raffle_id")
    private Raffle raffle;

    @Builder.Default
    private boolean active = true;

    @Version
    @Builder.Default
    private long version = 0L;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "discount_policy_id", referencedColumnName = "id")
    private IDiscountRule discountPolicy;
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "purchase_policy_id", referencedColumnName = "id")
    private IPurchaseRule purchasePolicy;

    public void recordSale(int numberOfTickets, double revenue) {
        this.ticketsSold += numberOfTickets;
        this.totalRevenue += revenue;
    }

    // --- Added for UC5 compatibility ---
    /**
     * An event is raffle-gated (sale mode = raffle) iff a raffle is configured on
     * it.
     */
    public boolean requiresRaffle() {
        return raffle != null;
    }

    /**
     * Authorizes a member to reserve under this event's raffle. Callers must have
     * already
     * ensured the draw was run (the draw mints codes and needs an external
     * generator, so it
     * stays in the service layer). This method then enforces, through the root: a
     * member is
     * required, winners must have been drawn, and the supplied code must be this
     * member's
     * valid, unexpired authorization.
     */
    public void authorizeRaffleReservation(String memberEmail, String raffleCode, LocalDateTime now) {
        if (memberEmail == null)
            throw new RaffleException("Raffle events require a logged-in member to reserve tickets.");
        if (this.raffle == null || this.raffle.getStatus() != RaffleStatus.DRAWN)
            throw new RaffleException("Tickets are not on sale yet for this raffle event.");
        if (!this.raffle.validateCode(raffleCode, memberEmail, now))
            throw new RaffleException("Invalid or expired raffle code.");
    }

    /**
     * UC II.4.3 — sets this event's sale mode to raffle by attaching a freshly
     * configured {@link Raffle}.
     * The {@code registrationDeadline} doubles as the sale-open moment.
     * Authorization (who may configure)
     * is enforced by the caller; this method owns the domain invariants and child
     * construction.
     */
    public void configureRaffle(String raffleId, LocalDateTime registrationDeadline,
            Duration codeValidity, int numberOfWinners, LocalDateTime now) {
        if (registrationDeadline == null || !registrationDeadline.isAfter(now))
            throw new InvalidEventException("Raffle registration deadline must be in the future.");
        if (numberOfWinners < 1)
            throw new InvalidEventException("A raffle must award at least one winner.");
        // The winner count is independent of the event's capacity (#439): a raffle cares only
        // about how many winners are configured, never about how many seats exist. Capacity is
        // enforced separately at purchase time.

        Raffle configured = Raffle.builder()
                .raffleId(raffleId)
                .eventId(this.eventId)
                .registrationDeadline(registrationDeadline)
                .codeValidity(codeValidity != null ? codeValidity : Duration.ofMinutes(30))
                .numberOfWinners(numberOfWinners)
                .status(RaffleStatus.OPEN)
                .build();
        this.raffle = configured;
        this.updatedAt = now;
    }

    /**
     * UC10 — register a member for this event's raffle. Throws if the event is not
     * raffle-gated.
     */
    public void registerForRaffle(String memberEmail, LocalDateTime now) {
        if (!requiresRaffle())
            throw new RaffleException("This event does not have a raffle.");
        this.raffle.register(memberEmail, now);
        this.updatedAt = now;
    }

    /**
     * True once the sale has opened (deadline passed) but winners have not yet been
     * drawn.
     */
    public boolean isRaffleDrawDue(LocalDateTime now) {
        return requiresRaffle() && this.raffle.isDrawDue(now);
    }

    /**
     * True when this event has a raffle whose winners have not yet been drawn (status OPEN),
     * regardless of whether the deadline has passed. Used by the scheduling layer to decide
     * which raffles still need a draw job (re)scheduled.
     */
    public boolean isRafflePendingDraw() {
        return requiresRaffle() && this.raffle.getStatus() == RaffleStatus.OPEN;
    }

    /**
     * The raffle's registration deadline (the moment the draw becomes due), or {@code null}
     * if the event is not raffle-gated. Lets the scheduler reschedule draws without reaching
     * into the raffle child directly.
     */
    public LocalDateTime getRaffleRegistrationDeadline() {
        return this.raffle == null ? null : this.raffle.getRegistrationDeadline();
    }

    /**
     * Draws winners up to the raffle's configured number of winners, minting each a
     * time-limited code. No-op (empty set) if the event is not raffle-gated.
     * Idempotency/draw-once is enforced inside {@link Raffle#drawWinners}.
     */
    public Set<String> drawRaffleWinners(Supplier<String> codeGenerator, LocalDateTime now) {
        if (!requiresRaffle())
            return Collections.emptySet();
        Set<String> winners = this.raffle.drawWinners(codeGenerator, now);
        this.updatedAt = now;
        return winners;
    }

    /**
     * Read-only snapshot of the raffle participant emails (empty if the event has
     * no raffle).
     */
    public Set<String> getRaffleParticipants() {
        if (!requiresRaffle())
            return Collections.emptySet();
        return Collections.unmodifiableSet(new LinkedHashSet<>(this.raffle.getParticipants()));
    }

    /**
     * A drawn winner's authorization (code + expiry), or {@code null} if absent /
     * no raffle.
     */
    public RaffleAuthorization getRaffleAuthorization(String memberEmail) {
        return this.raffle == null ? null : this.raffle.getAuthorization(memberEmail);
    }

    public String getWinnerCode(String memberEmail) {
        RaffleAuthorization auth = getRaffleAuthorization(memberEmail);
        return auth == null ? null : auth.getCode();
    }

    public LocalDateTime getWinnerCodeExpiry(String memberEmail) {
        RaffleAuthorization auth = getRaffleAuthorization(memberEmail);
        return auth == null ? null : auth.getExpiresAt();
    }

    public void setEventTypeName(String typeName) {
        this.eventType = typeName != null ? new EventType(typeName, "") : null;
    }

    // -----------------------------------

    public boolean matchesSearchCriteria(String companyName, List<String> searchKeywords,
            String category, LocalDateTime dateFrom, LocalDateTime dateTo,
            String searchLocation, Double priceMin, Double priceMax) {

        if (!this.active)
            return false;

        return matchesCompany(companyName)
                && matchesKeywords(searchKeywords)
                && matchesCategory(category)
                && matchesDateRange(dateFrom, dateTo)
                && matchesPriceRange(priceMin, priceMax)
                && matchesLocation(searchLocation);
    }

    private boolean matchesCompany(String companyName) {
        if (companyName == null || companyName.isEmpty())
            return true;
        return this.productionCompanyName != null
                && this.productionCompanyName.toLowerCase().contains(companyName.toLowerCase());
    }

    private boolean matchesKeywords(List<String> searchKeywords) {
        if (searchKeywords == null || searchKeywords.isEmpty())
            return true;

        for (String keyword : searchKeywords) {
            if (keyword == null || keyword.isEmpty())
                continue;
            String lower = keyword.toLowerCase();
            boolean found = false;
            if (this.name != null && this.name.toLowerCase().contains(lower))
                found = true;
            if (this.artist != null && this.artist.toLowerCase().contains(lower))
                found = true;
            if (!found)
                return false;
        }
        return true;
    }

    private boolean matchesCategory(String category) {
        if (category == null || category.isEmpty())
            return true;
        return this.eventType != null
                && this.eventType.getTypeName().equalsIgnoreCase(category);
    }

    private boolean matchesDateRange(LocalDateTime dateFrom, LocalDateTime dateTo) {
        if (dateFrom != null && this.startDate != null && this.startDate.isBefore(dateFrom)) {
            return false;
        }
        if (dateTo != null && this.endDate != null && this.endDate.isAfter(dateTo)) {
            return false;
        }
        return true;
    }

    private boolean matchesPriceRange(Double priceMin, Double priceMax) {
        if (priceMin != null && this.maxPrice < priceMin)
            return false;
        if (priceMax != null && this.minPrice > priceMax)
            return false;
        return true;
    }

    private boolean matchesLocation(String searchLocation) {
        if (searchLocation == null || searchLocation.isEmpty())
            return true;
        return this.location != null
                && this.location.toLowerCase().contains(searchLocation.toLowerCase());
    }

    public void isPurchaseAllowed(CustomerDetails customerDetails, ProductionCompany company) throws Exception {
        // 1. Tell the company to check its own global policies
        if (company != null) {
            company.isPurchaseAllowed(customerDetails);
        }
        // 2. Check event-specific policies
        if (this.purchasePolicy != null) {
            this.purchasePolicy.evaluate(customerDetails);
        }
    }

    public double calculateDiscountedPrice(double baseTotalPrice, CustomerDetails customerDetails,
            ProductionCompany company) {
        double totalDiscountPercentage = 0.0;

        // Ask the company for its discount percentage
        if (company != null) {
            totalDiscountPercentage += company.evaluateDiscount(customerDetails);
        }
        // Add event-specific discount percentage
        if (this.discountPolicy != null) {
            totalDiscountPercentage += this.discountPolicy.evaluate(customerDetails);
        }

        if (totalDiscountPercentage <= 0)
            return baseTotalPrice;
        if (totalDiscountPercentage > 100.0)
            totalDiscountPercentage = 100.0;

        return baseTotalPrice - (baseTotalPrice * (totalDiscountPercentage / 100.0));
    }
}
