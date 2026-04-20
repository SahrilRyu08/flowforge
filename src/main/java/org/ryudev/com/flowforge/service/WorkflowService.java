package org.ryudev.com.flowforge.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ryudev.com.flowforge.config.FlowForgePrincipal;
import org.ryudev.com.flowforge.domain.*;
import org.ryudev.com.flowforge.dto.request.CreateWorkflowRequest;
import org.ryudev.com.flowforge.dto.request.TriggerRequest;
import org.ryudev.com.flowforge.dto.request.UpdateWorkflowRequest;
import org.ryudev.com.flowforge.dto.response.RunSummaryResponse;
import org.ryudev.com.flowforge.dto.response.WorkflowDetailResponse;
import org.ryudev.com.flowforge.dto.response.WorkflowSummaryResponse;
import org.ryudev.com.flowforge.dto.response.WorkflowVersionResponse;
import org.ryudev.com.flowforge.engine.DagExecutor;
import org.ryudev.com.flowforge.engine.DagParser;
import org.ryudev.com.flowforge.repository.UserRepository;
import org.ryudev.com.flowforge.repository.WorkflowRepository;
import org.ryudev.com.flowforge.repository.WorkflowRunRepository;
import org.ryudev.com.flowforge.repository.WorkflowVersionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;
import java.util.HexFormat;
import java.security.SecureRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final UserRepository userRepository;
    private final DagParser dagParser;
    private final DagExecutor dagExecutor;

    @Transactional(readOnly = true)
    public Page<WorkflowSummaryResponse> listWorkflows(String status, String search, Pageable pageable) {
        UUID tenantId = currentTenantId();
        WorkflowStatus st = parseStatusFilter(status);
        return workflowRepository
                .findByTenantWithFilters(tenantId, st, blankToNull(search), pageable)
                .map(WorkflowSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public WorkflowDetailResponse getWorkflow(UUID id) {
        Workflow w = loadOwnedWorkflow(id);
        WorkflowVersion ver = currentVersion(w);
        return WorkflowDetailResponse.from(w, ver);
    }

    @Transactional
    public WorkflowDetailResponse createWorkflow(@Valid CreateWorkflowRequest request) {
        FlowForgePrincipal p = currentPrincipal();
        User user = userRepository.getReferenceById(p.userId());
        Tenant tenant = user.getTenant();

        DagDefinition dag = dagParser.parseAndValidate(request.dagDefinition);
        validateCron(request.cronExpression);

        Workflow wf = Workflow.builder()
                .tenant(tenant)
                .createdBy(user)
                .name(request.name.trim())
                .description(blankToNull(request.description))
                .status(WorkflowStatus.DRAFT)
                .currentVersion(1)
                .cronExpression(blankToNull(request.cronExpression))
                .webhookToken(newWebhookToken())
                .globalTimeoutSeconds(request.globalTimeoutSeconds != null ? request.globalTimeoutSeconds : 3600)
                .build();
        wf = workflowRepository.save(wf);

        WorkflowVersion v1 = WorkflowVersion.builder()
                .workflow(wf)
                .version(1)
                .dagDefinition(dag)
                .changeNote("Initial version")
                .createdBy(user)
                .build();
        workflowVersionRepository.save(v1);

        return WorkflowDetailResponse.from(wf, v1);
    }

    @Transactional
    public WorkflowDetailResponse updateWorkflow(UUID id, @Valid UpdateWorkflowRequest request) {
        FlowForgePrincipal p = currentPrincipal();
        User user = userRepository.getReferenceById(p.userId());
        Workflow wf = loadOwnedWorkflow(id);

        if (request.name != null && !request.name.isBlank()) {
            wf.setName(request.name.trim());
        }
        if (request.description != null) {
            wf.setDescription(blankToNull(request.description));
        }
        if (request.status != null) {
            wf.setStatus(request.status);
        }
        if (request.globalTimeoutSeconds != null) {
            wf.setGlobalTimeoutSeconds(request.globalTimeoutSeconds);
        }
        if (request.cronExpression != null) {
            validateCron(request.cronExpression);
            wf.setCronExpression(blankToNull(request.cronExpression));
        }

        if (request.dagDefinition != null) {
            DagDefinition dag = dagParser.parseAndValidate(request.dagDefinition);
            int next = workflowVersionRepository.findMaxVersionByWorkflowId(wf.getId()) + 1;
            wf.setCurrentVersion(next);
            WorkflowVersion nv = WorkflowVersion.builder()
                    .workflow(wf)
                    .version(next)
                    .dagDefinition(dag)
                    .changeNote(blankToNull(request.changeNote))
                    .createdBy(user)
                    .build();
            workflowVersionRepository.save(nv);
        }

        workflowRepository.save(wf);
        return WorkflowDetailResponse.from(wf, currentVersion(wf));
    }

    @Transactional
    public void deleteWorkflow(UUID id) {
        Workflow wf = loadOwnedWorkflow(id);
        wf.setStatus(WorkflowStatus.ARCHIVED);
        workflowRepository.save(wf);
    }

    @Transactional(readOnly = true)
    public Page<WorkflowVersionResponse> listVersions(UUID id, Pageable pageable) {
        Workflow wf = loadOwnedWorkflow(id);
        return workflowVersionRepository.findByWorkflow_Id(wf.getId(), pageable)
                .map(WorkflowVersionResponse::from);
    }

    @Transactional
    public WorkflowDetailResponse rollback(UUID id, Integer version) {
        Workflow wf = loadOwnedWorkflow(id);
        workflowVersionRepository.findByWorkflow_IdAndVersion(wf.getId(), version)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found"));
        wf.setCurrentVersion(version);
        workflowRepository.save(wf);
        return WorkflowDetailResponse.from(wf, currentVersion(wf));
    }

    @Transactional
    public RunSummaryResponse triggerManual(UUID id, TriggerRequest request) {
        FlowForgePrincipal p = currentPrincipal();
        User user = userRepository.findById(p.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        Workflow wf = loadOwnedWorkflow(id);
        if (wf.getStatus() != WorkflowStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workflow must be ACTIVE to run");
        }
        return startRunInternal(wf, TriggerType.MANUAL, user.getEmail());
    }

    @Transactional
    public RunSummaryResponse triggerByWebhook(String token, Object payload) {
        Workflow wf = workflowRepository.findByWebhookToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown webhook"));
        if (wf.getTenant().getStatus() != TenantStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant inactive");
        }
        if (wf.getStatus() != WorkflowStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workflow not active");
        }
        return startRunInternal(wf, TriggerType.WEBHOOK, "webhook");
    }

    /**
     * Invoked by the system scheduler (no authenticated principal).
     */
    @Transactional
    public void triggerScheduledIfDue(UUID workflowId) {
        Workflow wf = workflowRepository.findById(workflowId).orElse(null);
        if (wf == null || wf.getStatus() != WorkflowStatus.ACTIVE || wf.getCronExpression() == null) {
            return;
        }
        startRunInternal(wf, TriggerType.SCHEDULED, "cron");
    }

    private RunSummaryResponse startRunInternal(Workflow wf, TriggerType type, String triggeredBy) {
        WorkflowVersion ver = workflowVersionRepository
                .findByWorkflow_IdAndVersion(wf.getId(), wf.getCurrentVersion())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Current version missing"));

        WorkflowRun run = WorkflowRun.builder()
                .tenant(wf.getTenant())
                .workflow(wf)
                .workflowVersion(ver)
                .status(RunStatus.PENDING)
                .triggerType(type)
                .triggeredBy(triggeredBy)
                .build();
        run = workflowRunRepository.save(run);
        int timeout = wf.getGlobalTimeoutSeconds() != null ? wf.getGlobalTimeoutSeconds() : 3600;
        scheduleExecutionAfterCommit(run.getId(), ver.getDagDefinition(), timeout);
        return RunSummaryResponse.from(run);
    }

    private void scheduleExecutionAfterCommit(UUID runId, DagDefinition dag, int globalTimeoutSeconds) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                WorkflowRun loaded = workflowRunRepository.findFetchedById(runId).orElseThrow();
                dagExecutor.execute(loaded, dag, globalTimeoutSeconds);
            }
        });
    }

    private Workflow loadOwnedWorkflow(UUID id) {
        UUID tenantId = currentTenantId();
        return workflowRepository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found"));
    }

    private WorkflowVersion currentVersion(Workflow wf) {
        return workflowVersionRepository
                .findByWorkflow_IdAndVersion(wf.getId(), wf.getCurrentVersion())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found"));
    }

    private static void validateCron(String cron) {
        if (cron == null || cron.isBlank()) {
            return;
        }
        try {
            CronExpression.parse(cron.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cron expression: " + e.getMessage());
        }
    }

    private static String newWebhookToken() {
        byte[] buf = new byte[24];
        new SecureRandom().nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static WorkflowStatus parseStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return WorkflowStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status filter");
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
