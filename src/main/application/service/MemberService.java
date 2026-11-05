package application.service;
import java.util.List;

import domain.exceptions.AccountSuspendedException;
import domain.exceptions.EmailAlreadyRegisteredException;
import domain.exceptions.InvalidCredentialsException;
import domain.exceptions.MemberNotFoundException;
import domain.exceptions.OptimisticLockingFailureException;
import domain.exceptions.SamePasswordException;
import domain.exceptions.UnauthorizedException;
import domain.exceptions.UsernameAlreadyTakenException;
import org.springframework.stereotype.Service;
import application.dto.LoginContextDTO;
import application.dto.MemberDTO;

import application.dto.OrderDTO;
import application.dto.TicketDTO;
import application.interfaces.IAuth;
import application.interfaces.INotificationService;
import application.interfaces.IPasswordEncoder;
import domain.Aggregates.member.Member;
import domain.Irepository.IMemberRepository;
import domain.Irepository.IOrderRepository;
import domain.Irepository.ITicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MemberService {

    private static final Logger logger = LoggerFactory.getLogger(MemberService.class);
    private final IMemberRepository memberRepo;
    private final IOrderRepository orderRepo;
    private final ITicketRepository ticketRepo;
    private final IPasswordEncoder passwordEncoder;
    private final IAuth authService;
    private final INotificationService notificationService;

    public MemberService(IMemberRepository memberRepo, IOrderRepository orderRepo, ITicketRepository ticketRepo,
                         IPasswordEncoder passwordEncoder, IAuth authService, INotificationService notificationService) {
        this.memberRepo = memberRepo;
        this.orderRepo = orderRepo;
        this.ticketRepo = ticketRepo;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.notificationService = notificationService;
    }

    /**
     * Validates a session token and returns the authenticated member's email.
     * Mirrors the {@code authenticate} helper used by the other application
     * services (ShowService, EventService, AdminService).
     *
     * @throws UnauthorizedException if the token is missing, empty, or invalid
     */
    private String authenticate(String token) {
        if (token == null || token.isEmpty() || !authService.validateToken(token)) {
            logger.warn("Authentication failed: Invalid or expired token.");
            throw new UnauthorizedException("Invalid or expired token");
        }
        return authService.extractEmailFromToken(token);
    }

    /**
     * UC2 - Register
     * <p>
     * Creates and stores a new logged-out member. Returns {@code true} on success.
     * Failures are no longer collapsed into {@code false}: a duplicate email throws
     * {@link EmailAlreadyRegisteredException} so the UI can explain the failure
     * specifically (part of the incremental #162 exception-handling cleanup).
     *
     * @throws EmailAlreadyRegisteredException if a member with that email already exists
     */
    public boolean register(String username, String email, String password) {
        logger.info("Attempting to register new member with email: '{}'", email);

        if (memberRepo.findByEmail(email) != null) {
            logger.warn("Registration failed: Email '{}' is already registered", email);
            throw new EmailAlreadyRegisteredException("An account with that email already exists");
        }

        String hashedPassword = passwordEncoder.encode(password);
        Member newMember = new Member(username, email, hashedPassword);
        try {
            memberRepo.store(newMember);
        } catch (OptimisticLockingFailureException e) {
            // A concurrent request inserted the same email between our check and store.
            logger.warn("Registration failed: Email '{}' was registered concurrently", email);
            throw new EmailAlreadyRegisteredException("An account with that email already exists");
        }

        logger.info("Successfully registered new member: '{}' (Email: {})", username, email);
        return true;
    }

    /**
     * UC1 - Login
     * <p>
     * Returns a {@link LoginContextDTO} carrying the authenticated member profile
     * and JWT token. Company roles and admin status are resolved separately by the
     * presenter (via {@code RoleService}/{@code AdminService}) and are not part of
     * this DTO.
     * <p>
     * This method automatically registers the member for live notifications in the
     * current session/UI context.
     *
     * @throws MemberNotFoundException     if no member exists with the given email
     * @throws AccountSuspendedException   if the member exists but has been deactivated
     * @throws InvalidCredentialsException if the password does not match
     */
    public LoginContextDTO login(String email, String password) {
        logger.info("Attempting login for email: '{}'", email);
        Member member = memberRepo.findByEmail(email);

        if (member == null) {
            logger.warn("Login failed: Email '{}' not found", email);
            throw new MemberNotFoundException("No account with that email");
        }

        if (!passwordEncoder.matches(password, member.getHashedPassword())) {
            logger.warn("Login failed: Incorrect password for email '{}'", email);
            throw new InvalidCredentialsException("Incorrect password");
        }

        // Aggregate enforces the active-account invariant
        member.ensureCanLogin();

        String token = authService.generateToken(member.getEmail());

        // Automatic registration for the current session/UI context
        notificationService.registerCurrentSession(member.getEmail());

        logger.info("Successful login for member: '{}' (Email: {})", member.getUsername(), member.getEmail());

        MemberDTO memberDTO = MemberDTO.fromEntity(member);

        return LoginContextDTO.builder()
                .member(memberDTO)
                .token(token)
                .build();
    }

    // UC8 - Logout
    public void logout(String token) {
        String email = authenticate(token);
        Member member = memberRepo.findByEmail(email);

        if (member == null) {
            logger.warn("Logout failed: Member with email '{}' not found.", email);
            throw new MemberNotFoundException("No account with that email");
        }

        // Automatic unregistration for the current session cleanup
        notificationService.unregisterCurrentSession(email);

        logger.info("Successful logout for member: '{}' (Email: {})", member.getUsername(), member.getEmail());
    }

    // UC11 - Edit & Display Personal Details
    public void editUsername(String token, String newUsername) {
        String email = authenticate(token);
        Member member = memberRepo.findByEmail(email);
        if (member == null) {
            logger.warn("Edit username failed: Member with email '{}' not found.", email);
            throw new MemberNotFoundException("No account with that email");
        }

        // UC11 - Check if the new username is already taken by another user
        Member existingWithUsername = memberRepo.findByUsername(newUsername);
        if (existingWithUsername != null && !existingWithUsername.getEmail().equals(email)) {
            logger.warn("Username update failed: Username '{}' is already taken", newUsername);
            throw new UsernameAlreadyTakenException("That username is already taken");
        }

        member.rename(newUsername);
        memberRepo.store(member);
        logger.info("Successfully updated username for email '{}' to '{}'", email, newUsername);
    }

    public void editPassword(String token, String newPassword) {
        String email = authenticate(token);
        Member member = memberRepo.findByEmail(email);
        if (member == null) {
            logger.warn("Edit password failed: Member with email '{}' not found.", email);
            throw new MemberNotFoundException("No account with that email");
        }

        if (passwordEncoder.matches(newPassword, member.getHashedPassword())) {
            logger.warn("Password update failed: New password is the same as the current password for email '{}'", email);
            throw new SamePasswordException("New password must be different from your current password");
        }

        String hashedPassword = passwordEncoder.encode(newPassword);
        member.changePassword(hashedPassword);
        memberRepo.store(member);
        logger.info("Successfully updated password for email '{}'", email);
    }

    public MemberDTO getPersonalDetails(String token) {
        String email = authenticate(token);
        Member member = memberRepo.findByEmail(email);
        if (member == null) {
            logger.warn("Get personal details failed: Member with email '{}' not found.", email);
            throw new MemberNotFoundException("No account with that email");
        }
        return MemberDTO.fromEntity(member);
    }

    // UC9 - View Order History
    public List<OrderDTO> getOrderHistory(String token) {
        String email = authenticate(token);
        Member member = memberRepo.findByEmail(email);

        if (member == null) {
            logger.warn("Get order history failed: Member with email '{}' not found.", email);
            throw new MemberNotFoundException("No account with that email");
        }

        List<String> orderIds = member.getOrderIds();
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }

        return orderIds.stream()
                .map(orderRepo::findById)
                .filter(order -> order != null)
                .map(OrderDTO::fromEntity)
                .toList();
    }

    /**
     * UC9 helper - looks up issued tickets by id for a logged-in member.
     * Skips null lookups defensively so that a deleted ticket id (e.g. removed
     * during reservation rollback) doesn't break the order history view.
     */
    public List<TicketDTO> getTicketsByIds(List<String> ticketIds, String token) {
        authenticate(token);
        if (ticketIds == null || ticketIds.isEmpty()) {
            return List.of();
        }
        return ticketIds.stream()
                .map(ticketRepo::findById)
                .filter(ticket -> ticket != null)
                .map(TicketDTO::fromEntity)
                .toList();
    }
}
