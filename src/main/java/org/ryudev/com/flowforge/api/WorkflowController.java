package org.ryudev.com.flowforge.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ryudev.com.flowforge.dto.request.CreateWorkflowRequest;
import org.ryudev.com.flowforge.dto.request.TriggerRequest;
import org.ryudev.com.flowforge.dto.request.UpdateWorkflowRequest;
import org.ryudev.com.flowforge.dto.response.RunSummaryResponse;
import org.ryudev.com.flowforge.dto.response.WorkflowDetailResponse;
import org.ryudev.com.flowforge.dto.response.WorkflowSummaryResponse;
import org.ryudev.com.flowforge.dto.response.WorkflowVersionResponse;
import org.ryudev.com.flowforge.service.WorkflowService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
@Tag(name = "Workflows")
@RequiredArgsConstructor
class WorkflowController {

    private final WorkflowService workflowService;

    @GetMapping
    @Operation(summary = "List workflows with pagination and filtering")
    public ResponseEntity<Page<WorkflowSummaryResponse>> listWorkflows(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.fromString(sortDir), sortBy));
        return ResponseEntity.ok(workflowService.listWorkflows(status, search, pageable));
    }

    @PostMapping
    @Operation(summary = "Create a new workflow")
    public ResponseEntity<WorkflowDetailResponse> createWorkflow(
            @Valid @RequestBody CreateWorkflowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workflowService.createWorkflow(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get workflow by ID")
    public ResponseEntity<WorkflowDetailResponse> getWorkflow(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.getWorkflow(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update workflow definition (creates new version)")
    public ResponseEntity<WorkflowDetailResponse> updateWorkflow(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWorkflowRequest request) {
        return ResponseEntity.ok(workflowService.updateWorkflow(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Archive a workflow")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable UUID id) {
        workflowService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/versions")
    @Operation(summary = "List all versions of a workflow")
    public ResponseEntity<Page<WorkflowVersionResponse>> listVersions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "version"));
        return ResponseEntity.ok(workflowService.listVersions(id, pageable));
    }

    @PostMapping("/{id}/rollback/{version}")
    @Operation(summary = "Roll back workflow to a previous version")
    public ResponseEntity<WorkflowDetailResponse> rollback(
            @PathVariable UUID id,
            @PathVariable Integer version) {
        return ResponseEntity.ok(workflowService.rollback(id, version));
    }

    @PostMapping("/{id}/trigger")
    @Operation(summary = "Manually trigger a workflow run")
    public ResponseEntity<RunSummaryResponse> trigger(
            @PathVariable UUID id,
            @RequestBody(required = false) TriggerRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(workflowService.triggerManual(id, request));
    }
}
