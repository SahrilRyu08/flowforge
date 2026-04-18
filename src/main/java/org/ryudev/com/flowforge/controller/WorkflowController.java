package org.ryudev.com.flowforge.controller;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ryudev.com.flowforge.dto.CreateWorkflowRequest;
import org.ryudev.com.flowforge.workflow.WorkflowEntity;
import org.ryudev.com.flowforge.workflow.WorkflowService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {
    private final WorkflowService service;

    @PreAuthorize("hasRole('EDITOR')")
    @PostMapping
    public ResponseEntity<WorkflowEntity> create(
            @RequestHeader("X-Tenant-ID") String tenant,
            @Validated @RequestBody CreateWorkflowRequest request
    ) {
        return ResponseEntity.status(200).body(service.create(tenant,request.name(), request.definitionJson()));
    }

    @GetMapping
    public Page<WorkflowEntity> list(@RequestHeader("X-Tenant-ID") String tenantID, Pageable pageable) {
        return service.list(tenantID,pageable);
    }

    @PostMapping("/{id}/rollback/{targetVersion}")
    public ResponseEntity<WorkflowEntity> rollback(
            @RequestHeader("X-Tenant-ID") String tennantId,
            @PathVariable String id, @PathVariable int targetVersion
    ) {
        return ResponseEntity.ok(service.rollback(tennantId,id,targetVersion));
    }
}

