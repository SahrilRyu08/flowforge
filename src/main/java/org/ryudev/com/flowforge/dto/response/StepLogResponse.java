package org.ryudev.com.flowforge.dto.response;

import java.util.List;
import java.util.UUID;

public record StepLogResponse(
        UUID stepRunId,
        String stepId,
        List<String> lines
) {}
