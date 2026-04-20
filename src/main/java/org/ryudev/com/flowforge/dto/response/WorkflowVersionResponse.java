package org.ryudev.com.flowforge.dto.response;

import org.ryudev.com.flowforge.domain.DagDefinition;
import org.ryudev.com.flowforge.domain.WorkflowVersion;

import java.time.Instant;
import java.util.UUID;

public record WorkflowVersionResponse(
        UUID id,
        int version,
        DagDefinition dagDefinition,
        String changeNote,
        UUID createdBy,
        Instant createdAt
) {
    public static WorkflowVersionResponse from(WorkflowVersion v) {
        return new WorkflowVersionResponse(
                v.getId(),
                v.getVersion(),
                v.getDagDefinition(),
                v.getChangeNote(),
                v.getCreatedBy() != null ? v.getCreatedBy().getId() : null,
                v.getCreatedAt()
        );
    }
}
