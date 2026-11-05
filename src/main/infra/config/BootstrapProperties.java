package infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ticketanic.bootstrap")
public record BootstrapProperties(
        String initialStateFile,
        boolean failFastOnReplayError
) {
    public BootstrapProperties {
        if (initialStateFile == null) initialStateFile = "";
    }
}
