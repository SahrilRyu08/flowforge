package org.ryudev.com.flowforge.dto.response;

public record FailureInsightResponse(
        String summary,
        String suggestedFix,
        boolean aiEnabled
) {}
