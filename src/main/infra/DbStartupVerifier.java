package infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("cloud")
public class DbStartupVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DbStartupVerifier.class);

    public static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 5_000;

    private static final List<String> EXPECTED_TABLES = List.of(
        "active_order_tickets",
        "active_orders",
        "admins",
        "appointment_notification_permissions",
        "discount_conditions",
        "discount_rules",
        "events",
        "manager_permissions",
        "member_order_ids",
        "members",
        "notifications",
        "order_tickets",
        "orders",
        "production_companies",
        "production_company_pending_appointees",
        "production_company_roles",
        "production_company_to_roles",
        "purchase_rules",
        "raffle_authorizations",
        "raffle_participants",
        "raffles",
        "seats",
        "shows",
        "suspensions",
        "tickets",
        "venue_maps",
        "zones"
    );

    private final JdbcTemplate jdbc;

    public DbStartupVerifier(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        verifyConnection();
        verifyTables();
        log.info("DB startup verification PASSED — all checks green.");
    }

    private void verifyConnection() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                jdbc.queryForObject("SELECT 1", Integer.class);
                log.info("DB connectivity check PASSED (attempt {}/{}).", attempt, MAX_ATTEMPTS);
                return;
            } catch (Exception e) {
                log.warn("DB connectivity check FAILED (attempt {}/{}): {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    log.error("DB unreachable after {} attempts. Aborting startup.", MAX_ATTEMPTS);
                    throw new IllegalStateException("Cannot reach database after " + MAX_ATTEMPTS + " attempts. Aborting.", e);
                }
                sleep(RETRY_DELAY_MS);
            }
        }
    }

    private void verifyTables() {
        List<String> missing = new ArrayList<>();
        for (String table : EXPECTED_TABLES) {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_name = ?",
                Integer.class, table);
            if (count == null || count == 0) {
                missing.add(table);
            }
        }
        if (!missing.isEmpty()) {
            log.error("DB table check FAILED — missing tables: {}", missing);
            throw new IllegalStateException(
                "Required tables are missing: " + missing + ". Ensure schema is applied before starting.");
        }
        log.info("DB table check PASSED — all {} expected tables present.", EXPECTED_TABLES.size());
    }

    protected void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry DB connection.", e);
        }
    }
}
