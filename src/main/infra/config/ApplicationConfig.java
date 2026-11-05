package infra.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        QueueProperties.class,
        ActiveOrderProperties.class,
        OrderProperties.class,
        JwtProperties.class,
        ExternalServicesProperties.class,
        BootstrapProperties.class
})
public class ApplicationConfig {
}
