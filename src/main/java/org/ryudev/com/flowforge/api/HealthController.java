package org.ryudev.com.flowforge.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.ryudev.com.flowforge.dto.response.DashboardOverviewResponse;
import org.ryudev.com.flowforge.dto.response.MetricsResponse;
import org.ryudev.com.flowforge.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Dashboard & Health")
@RequiredArgsConstructor
class HealthController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    @Operation(summary = "Global health overview for dashboard")
    public ResponseEntity<DashboardOverviewResponse> getOverview() {
        return ResponseEntity.ok(dashboardService.getOverview());
    }

    @GetMapping("/metrics")
    @Operation(summary = "Execution metrics over the last 24 hours")
    public ResponseEntity<MetricsResponse> getMetrics(
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(dashboardService.getMetrics(hours));
    }
}
