package presentation;

import application.interfaces.INotificationService;
import application.interfaces.IPasswordEncoder;
import domain.Aggregates.admin.Admin;
import domain.Aggregates.event.*;
import domain.Aggregates.member.Member;
import domain.Aggregates.order.Order;
import domain.Aggregates.productioncompany.Permissions;
import domain.Aggregates.productioncompany.ProductionCompany;
import domain.Aggregates.show.Seat;
import domain.Aggregates.show.Show;
import domain.Aggregates.show.VenueMap;
import domain.Aggregates.show.Zone;
import domain.Irepository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
@Profile("!cloud")
public class DummyDataSeeder implements CommandLineRunner {

    private final IEventRepository eventRepository;
    private final IShowRepository showRepository;
    private final IProductionCompanyRepository companyRepository;
    private final IAdminRepository adminRepository;
    private final IOrderRepository orderRepository;
    private final IMemberRepository memberRepository;
    private final IPasswordEncoder passwordEncoder;
    private final INotificationService notificationService;


    public DummyDataSeeder(IEventRepository eventRepository, IShowRepository showRepository,
                           IProductionCompanyRepository companyRepository, IAdminRepository adminRepository,
                           IOrderRepository orderRepository, IMemberRepository memberRepository,
                           IPasswordEncoder passwordEncoder, INotificationService notificationService) {
        this.eventRepository = eventRepository;
        this.showRepository = showRepository;
        this.companyRepository = companyRepository;
        this.adminRepository = adminRepository;
        this.orderRepository = orderRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    @Override
    public void run(String... args) throws Exception {
        if (eventRepository.getAll().isEmpty()) {

            adminRepository.store(new Admin("admin@ticketanic.com"));

            // Seed Ido's account
            String idoEmail = "ido.avigad@gmail.com";
            memberRepository.store(new Member("Ido", idoEmail, passwordEncoder.encode("123456789")));
            notificationService.sendNotification(idoEmail, "Welcome to Ticketanic",
                    "Your account is set up and ready to go.", null);
            notificationService.sendNotification(idoEmail, "Summer Rock Concert",
                    "Tickets for The Rolling Stones at Madison Square Garden are now available.", null);
            notificationService.sendAppointment(idoEmail, "LiveNation", "founder@livenation.com",
                    "Appointment Request", Set.of(Permissions.APPOINT_MANAGERS, Permissions.VIEW_MANAGEMENT_HIERARCHY));

            // Seed a regular member for testing raffles as a consumer
            String memberEmail = "member@gmail.com";
            if (memberRepository.findByEmail(memberEmail) == null) {
                Member member = new Member("TestMember", memberEmail, passwordEncoder.encode("password123"));
                memberRepository.store(member);
            }

            // Seed a manager member for testing raffle configuration (Management side)
            String managerEmail = "manager@gmail.com";
            if (memberRepository.findByEmail(managerEmail) == null) {
                Member manager = new Member("EventManager", managerEmail, passwordEncoder.encode("password123"));
                memberRepository.store(manager);
            }

            // --- Suspension test users ---

            // 1. Currently suspended: active suspension, no end date → appears in "Currently Suspended"
            memberRepository.store(new Member("SuspendedUser", "suspended.user@test.com", passwordEncoder.encode("password123")));
            Member suspendedUser = memberRepository.findById("suspended.user@test.com");
            suspendedUser.suspend();
            memberRepository.store(suspendedUser);

            // 2. Suspension history: was suspended, then reinstated → appears only in "Full History"
            memberRepository.store(new Member("PreviouslySuspended", "history.user@test.com", passwordEncoder.encode("password123")));
            Member historyUser = memberRepository.findById("history.user@test.com");
            historyUser.suspend();
            memberRepository.store(historyUser);
            historyUser = memberRepository.findById("history.user@test.com");
            historyUser.unsuspend();
            memberRepository.store(historyUser);

            // 3. Clean active user: no suspension at all → appears in neither tab
            memberRepository.store(new Member("CleanUser", "clean.user@test.com", passwordEncoder.encode("password123")));
            //for company board in general.
            companyRepository.store(new ProductionCompany("LiveNation", "founder@livenation.com"));
            companyRepository.store(new ProductionCompany("Broadway Inc", "founder@broadway.com"));
            companyRepository.store(new ProductionCompany("Tech Giants", "founder@techgiants.com"));

            // Setup LiveNation with the manager as an owner to test raffle configuration
            ProductionCompany liveNation = companyRepository.findById("LiveNation");
            liveNation.appointOwner("founder@livenation.com", "founder@broadway.com");
            liveNation.appointOwner("founder@livenation.com", managerEmail); // EventManager is now an owner
            companyRepository.store(liveNation);

            liveNation = companyRepository.findById("LiveNation");
            liveNation.appointManager("founder@livenation.com", "manager1@livenation.com",
                    Set.of(Permissions.APPOINT_OWNERS, Permissions.VIEW_MANAGEMENT_HIERARCHY));
            companyRepository.store(liveNation);

            liveNation.appointManager("founder@livenation.com", "manager2@livenation.com",
                    Set.of(Permissions.APPOINT_MANAGERS, Permissions.VIEW_MANAGEMENT_HIERARCHY));
            companyRepository.store(liveNation);

            Event event1 = Event.builder()
                    .name("Summer Rock Concert")
                    .eventType(new EventType("Concert", "Live music performance"))
                    .productionCompanyName("LiveNation")
                    .artist("The Rolling Stones")
                    .location("Madison Square Garden, NYC")
                    .minPrice(80.0)
                    .maxPrice(200.0)
                    .startDate(LocalDateTime.now().plusDays(10))
                    .endDate(LocalDateTime.now().plusDays(10).plusHours(4))
                    .active(true)
                    .build();

            Event event2 = Event.builder()
                    .name("Hamilton Broadway Show")
                    .eventType(new EventType("Theater", "Broadway musical"))
                    .productionCompanyName("Broadway Inc")
                    .artist("Original Broadway Cast")
                    .location("Richard Rodgers Theatre, NYC")
                    .minPrice(120.0)
                    .maxPrice(250.0)
                    .startDate(LocalDateTime.now().plusDays(20))
                    .endDate(LocalDateTime.now().plusDays(20).plusHours(3))
                    .active(true)
                    .build();

            Event event3 = Event.builder()
                    .name("Tech Conference 2026")
                    .eventType(new EventType("Conference", "Technology and networking"))
                    .productionCompanyName("Tech Giants")
                    .location("Javits Convention Center, NYC")
                    .minPrice(100.0)
                    .maxPrice(500.0)
                    .startDate(LocalDateTime.now().plusDays(30))
                    .endDate(LocalDateTime.now().plusDays(32))
                    .active(true)
                    .build();

            eventRepository.store(event1);
            eventRepository.store(event2);
            eventRepository.store(event3);

            // Summer Rock Concert — 1 show with 3 zones (2 standing + 1 seated)
            List<Zone> concertZones = List.of(
                    buildStandingZone("1", "VIP Pit", 50, 38, 200.0),   // 12 spots taken
                    buildStandingZone("2", "General Admission", 100, 100, 80.0), // full
                    buildSeatedZone("3", "Reserved Seats", 4, 8, 120.0, 3)
            );
            seedShow(event1.getEventId(),
                    LocalDateTime.now().plusDays(10).withHour(20).withMinute(0),
                    "Main Stage Arena", concertZones);

            // Hamilton Broadway Show — 2 shows with 3 zones (2 seated + 1 standing)
            List<Zone> theaterZones1 = List.of(
                    buildSeatedZone("1", "Orchestra", 5, 8, 250.0, 2),
                    buildSeatedZone("2", "Mezzanine", 4, 8, 175.0, 1),
                    buildStandingZone("3", "Standing Room", 28, 10, 100.0)  // 18 spots taken
            );
            seedShow(event2.getEventId(),
                    LocalDateTime.now().plusDays(20).withHour(19).withMinute(30),
                    "Grand Theater", theaterZones1);

            List<Zone> theaterZones2 = List.of(
                    buildSeatedZone("1", "Orchestra", 5, 8, 250.0, 0),
                    buildSeatedZone("2", "Mezzanine", 4, 8, 175.0, 0),
                    buildStandingZone("3", "Standing Room", 28, 28, 100.0)
            );
            seedShow(event2.getEventId(),
                    LocalDateTime.now().plusDays(21).withHour(14).withMinute(0),
                    "Grand Theater", theaterZones2);

            // Tech Conference 2026 — 1 show with 3 zones (2 seated + 1 standing)
            List<Zone> conferenceZones = List.of(
                    buildSeatedZone("1", "VIP Seating", 3, 10, 500.0, 5),
                    buildSeatedZone("2", "General Seating", 5, 10, 200.0, 3),
                    buildStandingZone("3", "Standing Area", 20, 5, 100.0)  // 15 spots taken
            );
            seedShow(event3.getEventId(),
                    LocalDateTime.now().plusDays(30).withHour(9).withMinute(0),
                    "Convention Center", conferenceZones);

            //orders
            Order order1 = new Order(
                    UUID.randomUUID().toString(),
                    event1.getEventId(),
                    "show1",
                    List.of("ticket1", "ticket2"),
                    160.0,
                    LocalDateTime.now().minusDays(2),
                    null
            );
            orderRepository.store(order1);

            Order order2 = new Order(
                    UUID.randomUUID().toString(),
                    event2.getEventId(),
                    "show2",
                    List.of("ticket3"),
                    250.0,
                    LocalDateTime.now().minusDays(1),
                    null
            );
            orderRepository.store(order2);

            // ── Historical orders for date-filter testing ─────────────────────

            // OLD — member@gmail.com bought LiveNation tickets 4 months ago
            String oldOrder1Id = UUID.randomUUID().toString();
            orderRepository.store(new Order(oldOrder1Id, event1.getEventId(), "show-hist-1",
                    List.of("t-h1", "t-h2"), 180.0, LocalDateTime.now().minusMonths(4), null));
            Member m1 = memberRepository.findById(memberEmail);
            m1.completeActiveOrder(oldOrder1Id);
            memberRepository.store(m1);

            // OLD — manager@gmail.com bought Broadway Inc tickets 5 months ago
            String oldOrder2Id = UUID.randomUUID().toString();
            orderRepository.store(new Order(oldOrder2Id, event2.getEventId(), "show-hist-2",
                    List.of("t-h3"), 250.0, LocalDateTime.now().minusMonths(5), null));
            Member m2 = memberRepository.findById(managerEmail);
            m2.completeActiveOrder(oldOrder2Id);
            memberRepository.store(m2);

            // RECENT — member@gmail.com bought Tech Giants tickets 3 days ago
            String recentOrder1Id = UUID.randomUUID().toString();
            orderRepository.store(new Order(recentOrder1Id, event3.getEventId(), "show-hist-3",
                    List.of("t-h4"), 300.0, LocalDateTime.now().minusDays(3), null));
            m1 = memberRepository.findById(memberEmail);
            m1.completeActiveOrder(recentOrder1Id);
            memberRepository.store(m1);

            // RECENT — manager@gmail.com bought LiveNation tickets 5 days ago
            String recentOrder2Id = UUID.randomUUID().toString();
            orderRepository.store(new Order(recentOrder2Id, event1.getEventId(), "show-hist-4",
                    List.of("t-h5", "t-h6"), 160.0, LocalDateTime.now().minusDays(5), null));
            m2 = memberRepository.findById(managerEmail);
            m2.completeActiveOrder(recentOrder2Id);
            memberRepository.store(m2);

            // RECENT — ido bought Broadway Inc tickets 2 days ago
            String recentOrder3Id = UUID.randomUUID().toString();
            orderRepository.store(new Order(recentOrder3Id, event2.getEventId(), "show-hist-5",
                    List.of("t-h7"), 200.0, LocalDateTime.now().minusDays(2), null));
            Member ido = memberRepository.findById(idoEmail);
            ido.completeActiveOrder(recentOrder3Id);
            memberRepository.store(ido);

            // FAR — member@gmail.com bought Tech Giants tickets 14 months ago (out of range)
            String farOrderId = UUID.randomUUID().toString();
            orderRepository.store(new Order(farOrderId, event3.getEventId(), "show-hist-6",
                    List.of("t-h8"), 400.0, LocalDateTime.now().minusMonths(14), null));
            m1 = memberRepository.findById(memberEmail);
            m1.completeActiveOrder(farOrderId);
            memberRepository.store(m1);

            // Raffle Event 1: Open for registration
            Event raffleOpen = Event.builder()
                    .name("Ultra Music Festival")
                    .eventType(new EventType("Festival", "Electronic dance music"))
                    .productionCompanyName("LiveNation")
                    .artist("Various DJs")
                    .location("Bayfront Park, Miami")
                    .minPrice(300.0)
                    .maxPrice(1000.0)
                    .startDate(LocalDateTime.now().plusDays(60))
                    .endDate(LocalDateTime.now().plusDays(63))
                    .active(true)
                    .raffle(Raffle.builder()
                            .raffleId(UUID.randomUUID().toString())
                            .registrationDeadline(LocalDateTime.now().plusDays(5))
                            .status(RaffleStatus.OPEN)
                            .numberOfWinners(10000)
                            .participants(new LinkedHashSet<>(Set.of("other@gmail.com")))
                            .build())
                    .build();
            eventRepository.store(raffleOpen);
            seedShow(raffleOpen.getEventId(), LocalDateTime.now().plusDays(60).withHour(12).withMinute(0), "Main Stage", 
                    List.of(buildStandingZone("z1", "Main Floor", 1000, 1000, 300.0)));

            // Raffle Event 2: Drawn, User is a winner
            Map<String, RaffleAuthorization> winnersWimbledon = new HashMap<>();
            winnersWimbledon.put(memberEmail, new RaffleAuthorization("WIN-123", memberEmail, LocalDateTime.now().plusHours(1)));

            Event raffleWon = Event.builder()
                    .name("Wimbledon Finals")
                    .eventType(new EventType("Sports", "Tennis Grand Slam"))
                    .productionCompanyName("LiveNation")
                    .location("All England Club, London")
                    .minPrice(200.0)
                    .maxPrice(500.0)
                    .startDate(LocalDateTime.now().plusDays(40))
                    .endDate(LocalDateTime.now().plusDays(40).plusHours(5))
                    .active(true)
                    .raffle(Raffle.builder()
                            .raffleId(UUID.randomUUID().toString())
                            .registrationDeadline(LocalDateTime.now().minusDays(1))
                            .status(RaffleStatus.DRAWN)
                            .numberOfWinners(1)
                            .participants(new LinkedHashSet<>(Set.of(memberEmail, "other@gmail.com")))
                            .winners(winnersWimbledon)
                            .build())
                    .build();
            eventRepository.store(raffleWon);
            seedShow(raffleWon.getEventId(), LocalDateTime.now().plusDays(40).withHour(14).withMinute(0), "Centre Court",
                    List.of(buildSeatedZone("z1", "Royal Box", 2, 5, 500.0, 0)));

            // Raffle Event 3: Drawn, User lost
            Event raffleLost = Event.builder()
                    .name("Super Bowl LXI")
                    .eventType(new EventType("Sports", "NFL Championship"))
                    .productionCompanyName("LiveNation")
                    .location("Allegiant Stadium, Las Vegas")
                    .minPrice(1000.0)
                    .maxPrice(5000.0)
                    .startDate(LocalDateTime.now().plusDays(100))
                    .endDate(LocalDateTime.now().plusDays(100).plusHours(4))
                    .active(true)
                    .raffle(Raffle.builder()
                            .raffleId(UUID.randomUUID().toString())
                            .registrationDeadline(LocalDateTime.now().minusDays(2))
                            .status(RaffleStatus.DRAWN)
                            .numberOfWinners(1)
                            .participants(new LinkedHashSet<>(Set.of(memberEmail, "other@gmail.com")))
                            .winners(new HashMap<>(Map.of("other@gmail.com", new RaffleAuthorization("OTHER-WIN", "other@gmail.com", LocalDateTime.now().plusHours(1)))))
                            .build())
                    .build();
            eventRepository.store(raffleLost);
            seedShow(raffleLost.getEventId(), LocalDateTime.now().plusDays(100).withHour(18).withMinute(0), "Field View",
                    List.of(buildSeatedZone("z1", "Lower Tier", 10, 10, 1000.0, 5)));

            // Raffle Event 4: Drawn, User won but expired
            Map<String, RaffleAuthorization> winnersGlasto = new HashMap<>();
            winnersGlasto.put(memberEmail, new RaffleAuthorization("OLD-CODE", memberEmail, LocalDateTime.now().minusHours(1)));

            Event raffleExpired = Event.builder()
                    .name("Glastonbury Festival")
                    .eventType(new EventType("Festival", "Contemporary performing arts"))
                    .productionCompanyName("LiveNation")
                    .location("Worthy Farm, Pilton")
                    .minPrice(250.0)
                    .maxPrice(400.0)
                    .startDate(LocalDateTime.now().plusDays(30))
                    .endDate(LocalDateTime.now().plusDays(35))
                    .active(true)
                    .raffle(Raffle.builder()
                            .raffleId(UUID.randomUUID().toString())
                            .registrationDeadline(LocalDateTime.now().minusDays(3))
                            .status(RaffleStatus.DRAWN)
                            .numberOfWinners(1)
                            .participants(new LinkedHashSet<>(Set.of(memberEmail)))
                            .winners(winnersGlasto)
                            .build())
                    .build();
            eventRepository.store(raffleExpired);
            seedShow(raffleExpired.getEventId(), LocalDateTime.now().plusDays(30).withHour(10).withMinute(0), "Pyramid Stage",
                    List.of(buildStandingZone("z1", "Front Pit", 500, 500, 400.0)));

            System.out.println("✅ DummyDataSeeder: Seeded 7 events, 8 shows with venue maps and various raffle states.");
        }
    }

    private void seedShow(String eventId, LocalDateTime date, String mapName, List<Zone> zones) {
        int totalCapacity = zones.stream().mapToInt(Zone::getTotalCapacity).sum();
        Show show = Show.builder()
                .eventId(eventId)
                .date(date)
                .totalCapacity(totalCapacity)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        showRepository.store(show);

        VenueMap venueMap = VenueMap.builder()
                .showId(show.getShowId())
                .mapName(mapName)
                .totalCapacity(totalCapacity)
                .zoneCount(zones.size())
                .zones(new ArrayList<>(zones))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        show.setVenueMap(venueMap);
        showRepository.store(show);
        showRepository.saveVenueMapLayout(show.getShowId(), venueMap);
    }

    private Zone buildStandingZone(String label, String name, int capacity, int available, double price) {
        // Mint a globally-unique zone id so seeded shows never collide on shared primary keys.
        String zoneId = "z-" + UUID.randomUUID() + "-" + label;
        return Zone.builder()
                .zoneId(zoneId)
                .zoneName(name)
                .standing(true)
                .basePrice(price)
                .standingCapacity(capacity)
                .standingAvailable(available)
                .totalCapacity(capacity)
                .seats(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Zone buildSeatedZone(String label, String name, int rows, int seatsPerRow,
                                  double price, int takenInFirstRow) {
        // Mint a globally-unique zone id and derive seat ids from it so seeded shows never
        // collide on shared primary keys.
        String zoneId = "z-" + UUID.randomUUID() + "-" + label;
        List<Seat> seats = new ArrayList<>();
        for (int row = 1; row <= rows; row++) {
            for (int seatNum = 1; seatNum <= seatsPerRow; seatNum++) {
                boolean taken = row == 1 && seatNum <= takenInFirstRow;
                seats.add(new Seat(zoneId + "-r" + row + "s" + seatNum, row, seatNum, taken, price));
            }
        }
        return Zone.builder()
                .zoneId(zoneId)
                .zoneName(name)
                .standing(false)
                .basePrice(price)
                .rows(rows)
                .seatsPerRow(seatsPerRow)
                .totalCapacity(rows * seatsPerRow)
                .seats(seats)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
