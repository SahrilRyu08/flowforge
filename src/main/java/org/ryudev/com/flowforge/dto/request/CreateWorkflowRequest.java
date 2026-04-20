package org.ryudev.com.flowforge.dto.request;

import jakarta.validation.constraints.*;
import org.ryudev.com.flowforge.domain.DagDefinition;

public class CreateWorkflowRequest {
    @NotBlank @Size(max = 200) public String name;
    @Size(max = 500) public String description;
    @NotNull public DagDefinition dagDefinition;
    public String cronExpression;
    @Min(60) @Max(86400) public Integer globalTimeoutSeconds = 3600;
}
