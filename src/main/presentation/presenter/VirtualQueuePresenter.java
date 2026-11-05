package presentation.presenter;

import application.dto.QueueStatusDTO;
import application.service.VirtualQueueService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import presentation.CurrentUser;
import presentation.Result;

@Component
@VaadinSessionScope
public class VirtualQueuePresenter {

    private static final Logger logger = LoggerFactory.getLogger(VirtualQueuePresenter.class);

    private final VirtualQueueService virtualQueueService;
    private final CurrentUser currentUser;

    public VirtualQueuePresenter(VirtualQueueService virtualQueueService, CurrentUser currentUser) {
        this.virtualQueueService = virtualQueueService;
        this.currentUser = currentUser;
    }

    public Result<QueueStatusDTO> join(String showId) {
        try {
            QueueStatusDTO dto = currentUser.isLoggedIn()
                    ? virtualQueueService.joinVirtualQueue(showId, currentUser.getToken())
                    : virtualQueueService.joinVirtualQueueAsGuest(showId, currentUser.getGuestSessionId());
            return Result.ok(dto);
        } catch (Exception e) {
            logger.warn("Failed to join queue for show={}: {}", showId, e.getMessage());
            return Result.fail("Could not join the virtual queue.");
        }
    }

    public Result<QueueStatusDTO> checkStatus(String showId) {
        try {
            QueueStatusDTO dto = currentUser.isLoggedIn()
                    ? virtualQueueService.checkQueueStatus(showId, currentUser.getToken())
                    : virtualQueueService.checkQueueStatusAsGuest(showId, currentUser.getGuestSessionId());
            return Result.ok(dto);
        } catch (Exception e) {
            logger.warn("Failed to check queue status for show={}: {}", showId, e.getMessage());
            return Result.fail("Could not check queue status.");
        }
    }

    public Result<Void> release(String showId) {
        try {
            if (currentUser.isLoggedIn()) {
                virtualQueueService.releaseQueueSession(showId, currentUser.getToken());
            } else {
                virtualQueueService.releaseQueueSessionAsGuest(showId, currentUser.getGuestSessionId());
            }
            return Result.ok(null);
        } catch (Exception e) {
            logger.warn("Failed to release queue session for show={}: {}", showId, e.getMessage());
            return Result.fail("Could not leave the queue.");
        }
    }
}
