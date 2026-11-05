package presentation.presenter;

import application.dto.EventDTO;
import application.dto.ProductionCompanyDTO;
import application.service.EventService;
import application.service.ProductionCompanyService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import presentation.Result;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@VaadinSessionScope
public class EventBrowsePresenter {

    private static final Logger logger = LoggerFactory.getLogger(EventBrowsePresenter.class);

    private final EventService eventService;
    private final ProductionCompanyService companyService;

    public EventBrowsePresenter(EventService eventService, ProductionCompanyService companyService) {
        this.eventService = eventService;
        this.companyService = companyService;
    }

    public Result<List<EventDTO>> searchEvents(String productionCompanyName, String keywords, String category,
                                               LocalDateTime startDate, LocalDateTime endDate, String location,
                                               Double minPrice, Double maxPrice) {
        try {
            List<String> keywordList = null;
            if (keywords != null && !keywords.isBlank()) {
                keywordList = Arrays.stream(keywords.split("\\s+"))
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.toList());
            }
            
            // Treat blank fields as null to avoid sending empty strings as exact matches if not needed.
            String company = (productionCompanyName != null && !productionCompanyName.isBlank()) ? productionCompanyName : null;
            String cat = (category != null && !category.isBlank()) ? category : null;
            String loc = (location != null && !location.isBlank()) ? location : null;

            List<EventDTO> results = eventService.searchEvents(
                    company, keywordList, cat, startDate, endDate, loc, minPrice, maxPrice);

            List<EventDTO> filtered = results.stream()
                    .filter(e -> isCompanyActive(e.productionCompanyName))
                    .toList();
            return Result.ok(filtered);
        } catch (IllegalArgumentException | domain.exceptions.InvalidEventException e) {
            logger.warn("Invalid search parameters provided", e);
            return Result.fail(e.getMessage() != null ? e.getMessage() : "Invalid search criteria");
        }
    }

    private boolean isCompanyActive(String companyName) {
        try {
            ProductionCompanyDTO dto = companyService.getCompanyInfo(companyName);
            return dto != null && dto.isActive;
        } catch (Exception e) {
            return false;
        }
    }
}
