package org.ryudev.com.flowforge.service;

import lombok.RequiredArgsConstructor;
import org.ryudev.com.flowforge.config.FlowForgePrincipal;
import org.ryudev.com.flowforge.domain.RunStatus;
import org.ryudev.com.flowforge.domain.StepRun;
import org.ryudev.com.flowforge.domain.WorkflowRun;
import org.ryudev.com.flowforge.dto.response.RunDetailResponse;
import org.ryudev.com.flowforge.dto.response.RunSummaryResponse;
import org.ryudev.com.flowforge.dto.response.StepLogResponse;
import org.ryudev.com.flowforge.repository.StepRunRepository;
import org.ryudev.com.flowforge.repository.WorkflowRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RunService {

    private final WorkflowRunRepository workflowRunRepository;
    private final StepRunRepository stepRunRepository;

    @Transactional(readOnly = true)
    public Page<RunSummaryResponse> listRuns(UUID workflowId, String status, Pageable pageable) {
        UUID tenantId = currentTenantId();
        RunStatus st = parseRunStatus(status);
        return workflowRunRepository
                .findByTenantWithFilters(tenantId, workflowId, st, pageable)
                .map(RunSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public RunDetailResponse getRun(UUID id) {
        WorkflowRun run = loadRun(id);
        List<StepRun> steps = stepRunRepository.findByWorkflowRun_IdOrderByCreatedAtAsc(run.getId());
        return RunDetailResponse.from(run, steps);
    }

    @Transactional
    public void cancelRun(UUID id) {
        WorkflowRun run = loadRun(id);
        if (run.getStatus() == RunStatus.PENDING) {
            run.setStatus(RunStatus.CANCELLED);
            workflowRunRepository.save(run);
        } else if (run.getStatus() == RunStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancellation of RUNNING workflows is not supported in this MVP");
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Run cannot be cancelled in state " + run.getStatus());
        }
    }

    @Transactional(readOnly = true)
    public StepLogResponse getStepLogs(UUID runId, String stepId) {
        WorkflowRun run = loadRun(runId);
        StepRun step = stepRunRepository
                .findFirstByWorkflowRun_IdAndStepIdOrderByCreatedAtDesc(run.getId(), stepId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step run not found"));
        List<String> lines = step.getLogs() != null ? step.getLogs() : List.of();
        return new StepLogResponse(step.getId(), step.getStepId(), lines);
    }

    private WorkflowRun loadRun(UUID id) {
        UUID tenantId = currentTenantId();
        return workflowRunRepository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
    }

    private static RunStatus parseRunStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return RunStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid run status filter");
        }
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
