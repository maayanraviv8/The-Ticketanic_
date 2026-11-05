package application.service;

import application.dto.QueueStatusDTO;
import application.interfaces.IAuth;
import domain.Aggregates.member.Member;
import domain.Aggregates.show.Show;
import domain.Aggregates.virtualqueue.QueueStatus;
import domain.Aggregates.virtualqueue.VirtualQueue;
import domain.Irepository.IMemberRepository;
import domain.Irepository.IShowRepository;
import domain.Irepository.IVirtualQueueRepository;
import domain.exceptions.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service responsible for coordinating Virtual Queue operations.
 * Handles token validation and delegates queue joining, checking, and leaving
 * logic to the VirtualQueue aggregate root.
 */
@Service
@Slf4j
@Transactional
public class VirtualQueueService {

    private final IVirtualQueueRepository virtualQueueRepo;
    private final IShowRepository showRepo;
    private final IMemberRepository memberRepo;
    private final IAuth authService;

    public VirtualQueueService(IVirtualQueueRepository virtualQueueRepo,
                               IShowRepository showRepo,
                               IMemberRepository memberRepo,
                               IAuth authService) {
        this.virtualQueueRepo = virtualQueueRepo;
        this.showRepo = showRepo;
        this.memberRepo = memberRepo;
        this.authService = authService;
    }

    /**
     * Attempts to join the virtual queue for a specific show.
     *
     * @param showId The ID of the show the user wants to access.
     * @param token  The JWT token of the logged-in user.
     * @return QueueStatusDTO representing the user's position and access status.
     * @throws Exception If the show doesn't exist or token is invalid.
     */
    public QueueStatusDTO joinVirtualQueue(String showId, String token) throws Exception {
        Member member = validateTokenAndGetMember(token);
        log.info("Member {} is attempting to join the virtual queue for show {}", member.getEmail(), showId);
        return joinInternal(showId, member.getEmail());
    }

    /**
     * Guest variant of {@link #joinVirtualQueue} — uses a session-scoped guest id
     * instead of a member token. Allows unauthenticated visitors to participate
     * in the queue so the gate enforces capacity uniformly.
     */
    public QueueStatusDTO joinVirtualQueueAsGuest(String showId, String guestId) throws Exception {
        validateGuestId(guestId);
        log.info("Guest {} is attempting to join the virtual queue for show {}", guestId, showId);
        return joinInternal(showId, guestId);
    }

    /**
     * Checks the current status of the user in the virtual queue.
     *
     * @param showId The ID of the show the user is queuing for.
     * @param token  The JWT token of the logged-in user.
     * @return QueueStatusDTO representing the user's position and access status.
     * @throws Exception If the show doesn't exist or token is invalid.
     */
    public QueueStatusDTO checkQueueStatus(String showId, String token) throws Exception {
        Member member = validateTokenAndGetMember(token);
        return checkStatusInternal(showId, member.getEmail());
    }

    public QueueStatusDTO checkQueueStatusAsGuest(String showId, String guestId) throws Exception {
        validateGuestId(guestId);
        return checkStatusInternal(showId, guestId);
    }

    /**
     * Explicitly removes a user from the virtual queue.
     * This is useful if the user decides to leave the page or cancel their session.
     *
     * @param showId The ID of the show.
     * @param token  The JWT token of the logged-in user.
     * @throws Exception If the show doesn't exist or token is invalid.
     */
    public void releaseQueueSession(String showId, String token) throws Exception {
        Member member = validateTokenAndGetMember(token);
        log.info("Member {} is leaving the virtual queue for show {}", member.getEmail(), showId);
        releaseInternal(showId, member.getEmail());
    }

    public void releaseQueueSessionAsGuest(String showId, String guestId) throws Exception {
        validateGuestId(guestId);
        log.info("Guest {} is leaving the virtual queue for show {}", guestId, showId);
        releaseInternal(showId, guestId);
    }

    // ── shared internals ──────────────────────────────────────────────────────

    private QueueStatusDTO joinInternal(String showId, String identifier) throws Exception {
        validateShowExists(showId);
        VirtualQueue queue = virtualQueueRepo.findById(showId);
        QueueStatus status = queue.joinQueue(identifier);
        virtualQueueRepo.store(queue);
        return QueueStatusDTO.fromEntity(status);
    }

    private QueueStatusDTO checkStatusInternal(String showId, String identifier) throws Exception {
        validateShowExists(showId);
        VirtualQueue queue = virtualQueueRepo.findById(showId);
        QueueStatus status = queue.getStatus(identifier);
        // Persist anyway — getStatus triggers processQueue() which may expire sessions and advance the line.
        virtualQueueRepo.store(queue);
        return QueueStatusDTO.fromEntity(status);
    }

    private void releaseInternal(String showId, String identifier) throws Exception {
        validateShowExists(showId);
        VirtualQueue queue = virtualQueueRepo.findById(showId);
        queue.leaveQueue(identifier);
        virtualQueueRepo.store(queue);
    }

    private void validateGuestId(String guestId) {
        if (guestId == null || guestId.isEmpty()) {
            throw new IllegalArgumentException("Guest id is required for queue access.");
        }
    }

    private Member validateTokenAndGetMember(String token) throws UnauthorizedException {
        if (token == null || token.isEmpty()) {
            throw new UnauthorizedException("Token is required to access the queue.");
        }
        if (!authService.validateToken(token)) {
            log.error("Queue access failed: Invalid token provided");
            throw new UnauthorizedException("Invalid token provided.");
        }
        String memberEmail = authService.extractEmailFromToken(token);
        Member member = memberRepo.findByEmail(memberEmail);
        if (member == null) {
            throw new UnauthorizedException("User not found.");
        }
        return member;
    }

    private void validateShowExists(String showId) throws Exception {
        Show show = showRepo.findById(showId);
        if (show == null) {
            throw new Exception("Show not found");
        }
    }
}
