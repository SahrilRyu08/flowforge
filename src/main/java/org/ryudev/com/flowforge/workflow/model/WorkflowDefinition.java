package org.ryudev.com.flowforge.workflow.model;

import java.time.Duration;
import java.util.List;

public record WorkflowDefinition(
        String id,
        String name,
        Duration globalTimeout,
        List<Step> steps
){
}
