package org.ryudev.com.flowforge.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ryudev.com.flowforge.domain.Workflow;
import org.ryudev.com.flowforge.repository.WorkflowRepository;
import org.ryudev.com.flowforge.service.WorkflowService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates Spring {@link CronExpression} definitions on ACTIVE workflows and triggers runs.
 * Dedupes at most one fire per workflow per wall-clock minute.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowSchedulerService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowService workflowService;

    private final Map<UUID, Long> lastFireEpochMinute = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${flowforge.scheduler.cron-check-ms:60000}")
    public void tick() {
        long minute = Instant.now().getEpochSecond() / 60;
        Instant now = Instant.now();
        for (Workflow w : workflowRepository.findAllWithActiveCron()) {
            if (w.getTenant().getStatus() != org.ryudev.com.flowforge.domain.TenantStatus.ACTIVE) {
                continue;
            }
            if (lastFireEpochMinute.getOrDefault(w.getId(), -1L) == minute) {
                continue;
            }
            if (!matchesCurrentMinute(w, now)) {
                continue;
            }
            lastFireEpochMinute.put(w.getId(), minute);
            try {
                workflowService.triggerScheduledIfDue(w.getId());
            } catch (Exception e) {
                log.warn("Scheduled trigger failed for workflow {}: {}", w.getId(), e.getMessage());
            }
        }
    }

    private boolean matchesCurrentMinute(Workflow w, Instant now) {
        try {
            CronExpression ce = CronExpression.parse(w.getCronExpression().trim());
            Instant windowStart = now.truncatedTo(ChronoUnit.MINUTES);
            Instant next = ce.next(windowStart.minusSeconds(1));
            return next != null
                    && !next.isBefore(windowStart)
                    && next.isBefore(windowStart.plus(1, ChronoUnit.MINUTES));
        } catch (Exception e) {
            log.debug("Cron parse failed for {}: {}", w.getId(), e.getMessage());
            return false;
        }
    }
}
