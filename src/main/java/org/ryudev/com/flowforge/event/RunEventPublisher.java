package org.ryudev.com.flowforge.event;

import lombok.RequiredArgsConstructor;
import org.ryudev.com.flowforge.domain.StepRun;
import org.ryudev.com.flowforge.domain.WorkflowRun;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RunEventPublisher {

    private final SimpMessagingTemplate messaging;

    private String destination(UUID tenantId) {
        return "/topic/tenant/" + tenantId + "/runs";
    }

    /** @param tenantId resolved while the run graph is still initialized (avoid lazy access on async thread). */
    public void publishRunStarted(UUID tenantId, WorkflowRun workflowRun) {
        messaging.convertAndSend(destination(tenantId),
                new RunEventMessage("RUN_STARTED", workflowRun.getId(), null, null,
                        workflowRun.getStatus().name()));
    }

    public void publishStepStarted(UUID tenantId, WorkflowRun workflowRun, StepRun stepRun) {
        messaging.convertAndSend(destination(tenantId),
                new RunEventMessage("STEP_STARTED", workflowRun.getId(), stepRun.getStepId(),
                        stepRun.getStatus().name(), workflowRun.getStatus().name()));
    }

    public void publishStepCompleted(UUID tenantId, WorkflowRun workflowRun, StepRun stepRun) {
        messaging.convertAndSend(destination(tenantId),
                new RunEventMessage("STEP_COMPLETED", workflowRun.getId(), stepRun.getStepId(),
                        stepRun.getStatus().name(), workflowRun.getStatus().name()));
    }

    public void publishStepFailed(UUID tenantId, WorkflowRun workflowRun, StepRun stepRun) {
        messaging.convertAndSend(destination(tenantId),
                new RunEventMessage("STEP_FAILED", workflowRun.getId(), stepRun.getStepId(),
                        stepRun.getStatus().name(), workflowRun.getStatus().name()));
    }

    public void publishRunFinished(UUID tenantId, WorkflowRun run) {
        messaging.convertAndSend(destination(tenantId),
                new RunEventMessage("RUN_FINISHED", run.getId(), null, null, run.getStatus().name()));
    }
}
