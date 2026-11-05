package presentation.presenter;

import application.dto.SalesReportDTO;
import application.service.ProductionCompanyService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import domain.exceptions.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import presentation.CurrentUser;
import presentation.Result;

@Component
@VaadinSessionScope
public class SalesReportPresenter {

    private static final Logger logger = LoggerFactory.getLogger(SalesReportPresenter.class);

    private final ProductionCompanyService companyService;
    private final CurrentUser currentUser;

    public SalesReportPresenter(ProductionCompanyService companyService, CurrentUser currentUser) {
        this.companyService = companyService;
        this.currentUser = currentUser;
    }

    public Result<SalesReportDTO> getSalesReport(String companyId) {
        try {
            SalesReportDTO report = companyService.generateSalesReport(currentUser.getToken(), companyId);
            return Result.ok(report);
        } catch (IllegalArgumentException | UnauthorizedException e){
            return Result.fail(e.getMessage());
        }catch (Exception e) {
            logger.error("Failed to generate sales report for company '{}'", companyId, e);
            return Result.fail("Something went wrong. Please try again.");
        }
    }
}