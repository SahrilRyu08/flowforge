package org.ryudev.com.flowforge.dto.response;

import org.ryudev.com.flowforge.domain.Workflow;
import org.ryudev.com.flowforge.domain.WorkflowStatus;

import java.time.Instant;
import java.util.UUID;

public record WorkflowSummaryResponse(
        UUID id,
        String name,
        String description,
        WorkflowStatus status,
        int currentVersion,
        String cronExpression,
        Instant updatedAt
) {
    public static WorkflowSummaryResponse from(Workflow w) {
        return new WorkflowSummaryResponse(
                w.getId(),
                w.getName(),
                w.getDescription(),
                w.getStatus(),
                w.getCurrentVersion(),
                w.getCronExpression(),
                w.getUpdatedAt()
        );
    }
}
