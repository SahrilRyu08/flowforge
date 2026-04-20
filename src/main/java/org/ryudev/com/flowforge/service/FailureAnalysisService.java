package org.ryudev.com.flowforge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ryudev.com.flowforge.config.FlowForgePrincipal;
import org.ryudev.com.flowforge.domain.RunStatus;
import org.ryudev.com.flowforge.dto.response.FailureInsightResponse;
import org.ryudev.com.flowforge.repository.WorkflowRunRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Sends failed run context to an OpenAI-compatible chat API and returns a short diagnosis.
 * Guardrails: max prompt size, JSON-only response parsing, fallback when disabled.
 */
@Service
@RequiredArgsConstructor
public class FailureAnalysisService {

    private static final int MAX_CONTEXT_CHARS = 12_000;

    private final WorkflowRunRepository workflowRunRepository;
    private final ObjectMapper objectMapper;

    @Value("${flowforge.ai.openai.api-key:}")
    private String apiKey;

    @Value("${flowforge.ai.openai.model:gpt-4o-mini}")
    private String model;

    @Value("${flowforge.ai.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Transactional(readOnly = true)
    public FailureInsightResponse analyzeRun(UUID runId) {
        UUID tenantId = currentTenantId();
        var run = workflowRunRepository.findByIdAndTenant_Id(runId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
        if (run.getStatus() != RunStatus.FAILED && run.getStatus() != RunStatus.TIMED_OUT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Run did not fail");
        }

        String error = run.getErrorMessage() != null ? run.getErrorMessage() : run.getStatus().name();
        String dagJson;
        try {
            dagJson = objectMapper.writeValueAsString(run.getWorkflowVersion().getDagDefinition());
        } catch (JsonProcessingException e) {
            dagJson = "{}";
        }
        String ctx = "Workflow: " + run.getWorkflow().getName() + "\nError: " + error + "\nDAG: " + dagJson;
        if (ctx.length() > MAX_CONTEXT_CHARS) {
            ctx = ctx.substring(0, MAX_CONTEXT_CHARS) + "\n...[truncated]";
        }

        if (apiKey == null || apiKey.isBlank()) {
            return new FailureInsightResponse(
                    "AI disabled: set FLOWFORGE_AI_OPENAI_API_KEY.",
                    "Review the error message and step logs manually.",
                    false);
        }

        try {
            String body = buildChatRequestJson(ctx);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/$", "") + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(45))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                return new FailureInsightResponse(
                        "AI request failed: HTTP " + res.statusCode(),
                        res.body(),
                        true);
            }
            String content = extractAssistantContent(res.body());
            return new FailureInsightResponse(content, "Validate the suggestion against your policies before applying.", true);
        } catch (Exception e) {
            return new FailureInsightResponse("AI call failed: " + e.getMessage(), "", true);
        }
    }

    private String buildChatRequestJson(String userContent) throws Exception {
        var root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("temperature", 0.2);
        var messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content",
                "You are an SRE assistant. Given a failed workflow run, return 2 short paragraphs: "
                        + "(1) likely root cause, (2) concrete fix. No markdown, max 400 tokens.");
        messages.addObject().put("role", "user").put("content", userContent);
        return objectMapper.writeValueAsString(root);
    }

    private String extractAssistantContent(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        return root.path("choices").path(0).path("message").path("content").asText("No content");
    }

    private static UUID currentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof FlowForgePrincipal p)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return p.tenantId();
    }
}
