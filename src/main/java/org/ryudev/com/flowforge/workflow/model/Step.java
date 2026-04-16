package org.ryudev.com.flowforge.workflow.model;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record Step(
        String id,
        StepType type,
        Map<String, String> config,
        List<String> dependsOn,
        int maxRetires,
        Duration retryBackoff
) {
}
