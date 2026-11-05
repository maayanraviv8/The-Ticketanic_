package presentation;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"application", "domain", "repository", "external", "infra", "presentation"})
@EntityScan(basePackages = "domain")
@EnableJpaRepositories(basePackages = "repository.jpaRepo")
@Push
public class TicketanicApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(TicketanicApplication.class, args);
    }
}
