package org.ryudev.com.flowforge.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.ryudev.com.flowforge.dto.response.RunSummaryResponse;
import org.ryudev.com.flowforge.service.WorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks")
@RequiredArgsConstructor
class WebhookController {

    private final WorkflowService workflowService;

    @PostMapping("/{token}")
    @Operation(summary = "Trigger workflow via webhook token")
    public ResponseEntity<RunSummaryResponse> webhookTrigger(
            @PathVariable String token,
            @RequestBody(required = false) Object payload) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(workflowService.triggerByWebhook(token, payload));
    }
}
