package infra.scheduling;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Enables Spring's scheduled-task support so background jobs such as the real-time raffle draw
 * ({@code RaffleDrawScheduler}) run independently of any user request.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /**
     * Dedicated scheduler for one-shot raffle draw jobs. {@link RaffleDrawScheduler} submits a task
     * per raffle to fire at its registration deadline; a small pool lets near-simultaneous draws and
     * the periodic safety-net sweep run without starving each other.
     */
    @Bean
    public TaskScheduler raffleTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("raffle-draw-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        return scheduler;
    }
}
