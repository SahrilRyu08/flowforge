package org.ryudev.com.flowforge.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.ryudev.com.flowforge.dto.response.DashboardOverviewResponse;
import org.ryudev.com.flowforge.dto.response.MetricsResponse;
import org.ryudev.com.flowforge.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard")
@RequiredArgsConstructor
class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    @Operation(summary = "Global health: active runs, 24h rates, avg duration")
    public ResponseEntity<DashboardOverviewResponse> overview() {
        return ResponseEntity.ok(dashboardService.getOverview());
    }

    @GetMapping("/metrics")
    @Operation(summary = "Metrics for a rolling window (hours)")
    public ResponseEntity<MetricsResponse> metrics(
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(dashboardService.getMetrics(hours));
    }
}
