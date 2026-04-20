package org.ryudev.com.flowforge.dto.response;

public record DashboardOverviewResponse(
        long activeRuns,
        long runsLast24h,
        long successLast24h,
        long failedLast24h,
        double successRateLast24h,
        double avgDurationMsLast24h
) {}
