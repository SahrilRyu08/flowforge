package org.ryudev.com.flowforge.event;

import java.util.UUID;

public record RunEventMessage(
        String type,
        UUID runId,
        String stepId,
        String stepStatus,
        String runStatus
) {}
