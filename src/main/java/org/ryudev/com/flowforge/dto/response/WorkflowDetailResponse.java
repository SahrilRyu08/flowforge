package org.ryudev.com.flowforge.dto.response;

import org.ryudev.com.flowforge.domain.DagDefinition;
import org.ryudev.com.flowforge.domain.Workflow;
import org.ryudev.com.flowforge.domain.WorkflowStatus;
import org.ryudev.com.flowforge.domain.WorkflowVersion;

import java.time.Instant;
import java.util.UUID;

public record WorkflowDetailResponse(
        UUID id,
        String name,
        String description,
        WorkflowStatus status,
        int currentVersion,
        String cronExpression,
        String webhookToken,
        int globalTimeoutSeconds,
        DagDefinition dagDefinition,
        Instant createdAt,
        Instant updatedAt
) {
    public static WorkflowDetailResponse from(Workflow w, WorkflowVersion currentVersion) {
        return new WorkflowDetailResponse(
                w.getId(),
                w.getName(),
                w.getDescription(),
                w.getStatus(),
                w.getCurrentVersion(),
                w.getCronExpression(),
                w.getWebhookToken(),
                w.getGlobalTimeoutSeconds(),
                currentVersion.getDagDefinition(),
                w.getCreatedAt(),
                w.getUpdatedAt()
        );
    }
}
