package org.ryudev.com.flowforge.dto.response;

import org.ryudev.com.flowforge.domain.RunStatus;
import org.ryudev.com.flowforge.domain.StepRun;
import org.ryudev.com.flowforge.domain.StepStatus;
import org.ryudev.com.flowforge.domain.TriggerType;
import org.ryudev.com.flowforge.domain.WorkflowRun;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RunDetailResponse(
        UUID id,
        UUID workflowId,
        String workflowName,
        int workflowVersion,
        RunStatus status,
        TriggerType triggerType,
        String triggeredBy,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String errorMessage,
        Instant createdAt,
        List<StepRunItem> steps
) {
    public record StepRunItem(
            UUID id,
            String stepId,
            String stepName,
            StepStatus status,
            int attemptNumber,
            Instant startedAt,
            Instant finishedAt,
            Long durationMs,
            Object output,
            String errorMessage
    ) {
        public static StepRunItem from(StepRun s) {
            return new StepRunItem(
                    s.getId(),
                    s.getStepId(),
                    s.getStepName(),
                    s.getStatus(),
                    s.getAttemptNumber(),
                    s.getStartedAt(),
                    s.getFinishedAt(),
                    s.getDurationMs(),
                    s.getOutput(),
                    s.getErrorMessage()
            );
        }
    }

    public static RunDetailResponse from(WorkflowRun r, List<StepRun> steps) {
        return new RunDetailResponse(
                r.getId(),
                r.getWorkflow().getId(),
                r.getWorkflow().getName(),
                r.getWorkflowVersion().getVersion(),
                r.getStatus(),
                r.getTriggerType(),
                r.getTriggeredBy(),
                r.getStartedAt(),
                r.getFinishedAt(),
                r.getDurationMs(),
                r.getErrorMessage(),
                r.getCreatedAt(),
                steps.stream().map(StepRunItem::from).toList()
        );
    }
}
