package org.ryudev.com.flowforge.service;

import lombok.RequiredArgsConstructor;
import org.ryudev.com.flowforge.config.FlowForgePrincipal;
import org.ryudev.com.flowforge.domain.RunStatus;
import org.ryudev.com.flowforge.dto.response.DashboardOverviewResponse;
import org.ryudev.com.flowforge.dto.response.MetricsResponse;
import org.ryudev.com.flowforge.repository.WorkflowRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final WorkflowRunRepository workflowRunRepository;

    @Transactional(readOnly = true)
    public DashboardOverviewResponse getOverview() {
        UUID tenantId = currentTenantId();
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);

        long active = workflowRunRepository.countByTenant_IdAndStatus(tenantId, RunStatus.RUNNING)
                + workflowRunRepository.countByTenant_IdAndStatus(tenantId, RunStatus.PENDING);

        long success = workflowRunRepository.countByTenantAndStatusSince(tenantId, RunStatus.SUCCESS, since);
        long failed = workflowRunRepository.countByTenantAndStatusSince(tenantId, RunStatus.FAILED, since)
                + workflowRunRepository.countByTenantAndStatusSince(tenantId, RunStatus.TIMED_OUT, since);

        long totalFinished = success + failed;
        double rate = totalFinished == 0 ? 0.0 : (double) success / totalFinished;

        double avg = workflowRunRepository.averageDurationMsSince(tenantId, since);

        long runs24 = workflowRunRepository.countByTenantSince(tenantId, since);

        return new DashboardOverviewResponse(active, runs24, success, failed, rate, avg);
    }

    @Transactional(readOnly = true)
    public MetricsResponse getMetrics(int hours) {
        UUID tenantId = currentTenantId();
        int h = Math.min(Math.max(hours, 1), 168);
        Instant since = Instant.now().minus(h, ChronoUnit.HOURS);

        long success = workflowRunRepository.countByTenantAndStatusSince(tenantId, RunStatus.SUCCESS, since);
        long failed = workflowRunRepository.countByTenantAndStatusSince(tenantId, RunStatus.FAILED, since)
                + workflowRunRepository.countByTenantAndStatusSince(tenantId, RunStatus.TIMED_OUT, since);
        long total = success + failed;
        double rate = total == 0 ? 0.0 : (double) success / total;
        double avg = workflowRunRepository.averageDurationMsSince(tenantId, since);

        return new MetricsResponse(h, total, success, failed, rate, avg);
    }

    private static UUID currentTenantId() {
        return currentPrincipal().tenantId();
    }

    private static FlowForgePrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof FlowForgePrincipal p)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return p;
    }
}
