package acceptance.config;

import infra.config.ActiveOrderProperties;
import infra.config.OrderProperties;
import infra.config.QueueProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Minimal Spring configuration used by AcceptanceTestBase.
 *
 * Reads {@code application-test.yml} via {@link YamlPropertySourceFactory}
 * and binds it into the existing infra {@code @ConfigurationProperties} records
 * ({@link QueueProperties}, {@link ActiveOrderProperties}, ...). Tests then
 * pull those typed beans through {@code @Autowired}, so the only source of
 * truth for queue size, lock duration, persistence backend, etc. is the YAML
 * file — no hard-coded test constants.
 *
 * Intentionally a plain {@code @Configuration} rather than a
 * {@code @SpringBootApplication}, so this context never triggers Vaadin/Hilla
 * or JPA auto-configuration.
 */
@Configuration
@PropertySource(value = "classpath:application-test.yml", factory = YamlPropertySourceFactory.class)
@EnableConfigurationProperties({
        QueueProperties.class,
        ActiveOrderProperties.class,
        OrderProperties.class
})
public class AcceptanceTestConfig {
}
