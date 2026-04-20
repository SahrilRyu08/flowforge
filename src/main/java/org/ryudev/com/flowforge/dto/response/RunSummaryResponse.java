package org.ryudev.com.flowforge.dto.response;

import org.ryudev.com.flowforge.domain.RunStatus;
import org.ryudev.com.flowforge.domain.TriggerType;
import org.ryudev.com.flowforge.domain.WorkflowRun;

import java.time.Instant;
import java.util.UUID;

public record RunSummaryResponse(
        UUID id,
        UUID workflowId,
        String workflowName,
        RunStatus status,
        TriggerType triggerType,
        String triggeredBy,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        Instant createdAt
) {
    public static RunSummaryResponse from(WorkflowRun r) {
        return new RunSummaryResponse(
                r.getId(),
                r.getWorkflow().getId(),
                r.getWorkflow().getName(),
                r.getStatus(),
                r.getTriggerType(),
                r.getTriggeredBy(),
                r.getStartedAt(),
                r.getFinishedAt(),
                r.getDurationMs(),
                r.getCreatedAt()
        );
    }
}
