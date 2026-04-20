package org.ryudev.com.flowforge.dto.response;

public record MetricsResponse(
        int windowHours,
        long totalRuns,
        long successCount,
        long failedCount,
        double successRate,
        double avgDurationMs
) {}
