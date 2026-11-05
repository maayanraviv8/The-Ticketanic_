package unit.infra.external;

import static org.assertj.core.api.Assertions.assertThat;

import application.interfaces.IPaymentGateway;
import application.interfaces.ITicketIssuanceService;
import infra.config.ExternalServicesProperties;
import infra.external.WsepExternalClient;
import infra.payment.HttpPaymentGateway;
import infra.payment.StubPaymentGateway;
import infra.ticketing.HttpTicketIssuanceService;
import infra.ticketing.StubTicketIssuanceService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifies the {@code @ConditionalOnProperty} wiring selects exactly one implementation of
 * {@link IPaymentGateway} and {@link ITicketIssuanceService} depending on the
 * {@code ticketanic.external.*.enabled} toggles — the real risk introduced by gating two
 * beans per port. Uses {@link ApplicationContextRunner} (no Vaadin/web server) so it is fast
 * and hermetic.
 */
class ExternalBeanWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ExternalServicesProperties.class, () -> new ExternalServicesProperties(
                    "http://localhost/", 1000, 5000, null, null, null, null))
            .withUserConfiguration(
                    WsepExternalClient.class,
                    StubPaymentGateway.class, HttpPaymentGateway.class,
                    StubTicketIssuanceService.class, HttpTicketIssuanceService.class);

    @Test
    void usesStubsWhenTogglesDisabled() {
        runner.withPropertyValues(
                        "ticketanic.external.payment.enabled=false",
                        "ticketanic.external.ticket-issuance.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(IPaymentGateway.class);
                    assertThat(ctx.getBean(IPaymentGateway.class)).isInstanceOf(StubPaymentGateway.class);
                    assertThat(ctx).hasSingleBean(ITicketIssuanceService.class);
                    assertThat(ctx.getBean(ITicketIssuanceService.class)).isInstanceOf(StubTicketIssuanceService.class);
                });
    }

    @Test
    void usesStubsWhenTogglesMissing() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(IPaymentGateway.class);
            assertThat(ctx.getBean(IPaymentGateway.class)).isInstanceOf(StubPaymentGateway.class);
            assertThat(ctx).hasSingleBean(ITicketIssuanceService.class);
            assertThat(ctx.getBean(ITicketIssuanceService.class)).isInstanceOf(StubTicketIssuanceService.class);
        });
    }

    @Test
    void usesHttpAdaptersWhenTogglesEnabled() {
        runner.withPropertyValues(
                        "ticketanic.external.payment.enabled=true",
                        "ticketanic.external.ticket-issuance.enabled=true")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(IPaymentGateway.class);
                    assertThat(ctx.getBean(IPaymentGateway.class)).isInstanceOf(HttpPaymentGateway.class);
                    assertThat(ctx).hasSingleBean(ITicketIssuanceService.class);
                    assertThat(ctx.getBean(ITicketIssuanceService.class)).isInstanceOf(HttpTicketIssuanceService.class);
                });
    }
}
