package org.ryudev.com.flowforge.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.ryudev.com.flowforge.domain.DagDefinition;
import org.ryudev.com.flowforge.domain.WorkflowStatus;

public class UpdateWorkflowRequest {
    @Size(max = 200) public String name;
    @Size(max = 500) public String description;
    public WorkflowStatus status;
    public DagDefinition dagDefinition;
    public String cronExpression;
    @Size(max = 500) public String changeNote;
    @Min(60) @Max(86400) public Integer globalTimeoutSeconds;
}
