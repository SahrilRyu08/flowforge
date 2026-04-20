package org.ryudev.com.flowforge.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.ryudev.com.flowforge.dto.response.FailureInsightResponse;
import org.ryudev.com.flowforge.dto.response.RunDetailResponse;
import org.ryudev.com.flowforge.dto.response.RunSummaryResponse;
import org.ryudev.com.flowforge.dto.response.StepLogResponse;
import org.ryudev.com.flowforge.service.FailureAnalysisService;
import org.ryudev.com.flowforge.service.RunService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/runs")
@Tag(name = "Workflow Runs")
@RequiredArgsConstructor
class RunController {

    private final RunService runService;
    private final FailureAnalysisService failureAnalysisService;

    @GetMapping
    @Operation(summary = "List all workflow runs with filtering")
    public ResponseEntity<Page<RunSummaryResponse>> listRuns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID workflowId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.fromString(sortDir), sortBy));
        return ResponseEntity.ok(runService.listRuns(workflowId, status, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific run with step details")
    public ResponseEntity<RunDetailResponse> getRun(@PathVariable UUID id) {
        return ResponseEntity.ok(runService.getRun(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a running workflow")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<Void> cancelRun(@PathVariable UUID id) {
        runService.cancelRun(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/steps/{stepId}/logs")
    @Operation(summary = "Get logs for a specific step run")
    public ResponseEntity<StepLogResponse> getStepLogs(
            @PathVariable UUID id,
            @PathVariable String stepId) {
        return ResponseEntity.ok(runService.getStepLogs(id, stepId));
    }

    @PostMapping("/{id}/failure-insights")
    @Operation(summary = "AI-powered diagnosis for a failed run (optional API key)")
    public ResponseEntity<FailureInsightResponse> failureInsights(@PathVariable UUID id) {
        return ResponseEntity.ok(failureAnalysisService.analyzeRun(id));
    }
}
