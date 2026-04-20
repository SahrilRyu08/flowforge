package org.ryudev.com.flowforge.engine;


import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ryudev.com.flowforge.domain.DagDefinition;
import org.ryudev.com.flowforge.domain.StepDefinition;
import org.ryudev.com.flowforge.exception.DagValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Parses and validates workflow DAG definitions.
 * Acts as the entry point for any DAG coming in from the API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DagParser {

    private static final int MAX_STEPS = 200;
    private static final int MAX_EDGES = 500;
    private static final Set<String> VALID_HTTP_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD");

    private final ObjectMapper objectMapper;
    private final TopologicalSorter topologicalSorter;

    /**
     * Parses raw JSON map into a DagDefinition and validates it.
     */
    public DagDefinition parse(Map<String, Object> rawDag) {
        try {
            DagDefinition dag = objectMapper.convertValue(rawDag, DagDefinition.class);
            return parseAndValidate(dag);
        } catch (IllegalArgumentException e) {
            throw new DagValidationException("Invalid DAG structure: " + e.getMessage());
        }
    }

    /**
     * Validates an already-deserialized DagDefinition.
     */
    public DagDefinition parseAndValidate(DagDefinition dag) {
        // Structural validation first
        topologicalSorter.validate(dag);

        // Size limits
        if (dag.getSteps().size() > MAX_STEPS) {
            throw new DagValidationException(
                    "DAG exceeds maximum step limit of " + MAX_STEPS);
        }
        if (dag.getEdges() != null && dag.getEdges().size() > MAX_EDGES) {
            throw new DagValidationException(
                    "DAG exceeds maximum edge limit of " + MAX_EDGES);
        }

        // Per-step config validation
        for (StepDefinition step : dag.getSteps()) {
            validateStepConfig(step);
        }

        // Sort to confirm acyclicity
        List<Set<String>> levels = topologicalSorter.sort(dag);
        log.debug("DAG parsed: {} steps, {} levels", dag.getSteps().size(), levels.size());

        return dag;
    }

    private void validateStepConfig(StepDefinition step) {
        Map<String, Object> config = step.getConfig();

        switch (step.getType()) {
            case HTTP_CALL -> {
                requireConfigKey(config, "url", step.getId());
                String method = (String) config.getOrDefault("method", "GET");
                if (!VALID_HTTP_METHODS.contains(method.toUpperCase())) {
                    throw new DagValidationException(
                            "Invalid HTTP method '" + method + "' in step: " + step.getId());
                }
                // Prevent SSRF to internal metadata endpoints
                String url = (String) config.get("url");
                if (isBlockedUrl(url)) {
                    throw new DagValidationException(
                            "URL is blocked for security reasons in step: " + step.getId());
                }
            }
            case SCRIPT_EXEC -> {
                requireConfigKey(config, "script", step.getId());
                // Limit script size
                String script = (String) config.get("script");
                if (script.length() > 10_000) {
                    throw new DagValidationException(
                            "Script exceeds 10KB limit in step: " + step.getId());
                }
            }
            case DELAY -> {
                requireConfigKey(config, "durationMs", step.getId());
                long duration = ((Number) config.get("durationMs")).longValue();
                if (duration <= 0 || duration > 3_600_000) {
                    throw new DagValidationException(
                            "DELAY must be between 1ms and 1 hour in step: " + step.getId());
                }
            }
            case CONDITIONAL_BRANCH -> {
                requireConfigKey(config, "expression", step.getId());
                requireConfigKey(config, "trueStepId", step.getId());
                requireConfigKey(config, "falseStepId", step.getId());
            }
            default -> throw new IllegalStateException("Unexpected value: " + step.getType());
        }

        // Validate retry config
        if (step.getRetryConfig() != null) {
            var retry = step.getRetryConfig();
            if (retry.getMaxRetries() != null && retry.getMaxRetries() > 10) {
                throw new DagValidationException(
                        "maxRetries cannot exceed 10 in step: " + step.getId());
            }
        }
    }

    private void requireConfigKey(Map<String, Object> config, String key, String stepId) {
        if (config == null || !config.containsKey(key)) {
            throw new DagValidationException(
                    "Missing required config key '" + key + "' in step: " + stepId);
        }
    }

    private boolean isBlockedUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains("169.254.169.254") // AWS metadata
                || lower.contains("metadata.google.internal")
                || lower.startsWith("file://")
                || lower.startsWith("ftp://");
    }
}