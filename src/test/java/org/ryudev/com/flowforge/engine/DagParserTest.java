package org.ryudev.com.flowforge.engine;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.ryudev.com.flowforge.domain.DagDefinition;
import org.ryudev.com.flowforge.domain.RetryConfig;
import org.ryudev.com.flowforge.domain.StepDefinition;
import org.ryudev.com.flowforge.domain.StepType;
import org.ryudev.com.flowforge.exception.DagValidationException;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DagParser Unit Tests")
class DagParserTest {

    private DagParser parser;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        TopologicalSorter sorter = new TopologicalSorter();
        parser = new DagParser(objectMapper, sorter);
    }

    // ── HTTP_CALL Validation ──────────────────────────────────────────────────

    @Test
    @DisplayName("Valid HTTP_CALL step passes validation")
    void httpCall_validConfig_passes() {
        DagDefinition dag = dagWith(httpStep("s1", "GET",
                "https://api.example.com/data", null));

        assertThatCode(() -> parser.parseAndValidate(dag)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HTTP_CALL without URL throws DagValidationException")
    void httpCall_missingUrl_throws() {
        StepDefinition step = step("s1", StepType.HTTP_CALL, Map.of("method", "GET"));
        DagDefinition dag = dagWith(step);

        assertThatThrownBy(() -> parser.parseAndValidate(dag))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("Missing required config key 'url'");
    }

    @Test
    @DisplayName("HTTP_CALL with invalid method throws DagValidationException")
    void httpCall_invalidMethod_throws() {
        StepDefinition step = step("s1", StepType.HTTP_CALL,
                Map.of("url", "https://example.com", "method", "CONNECT"));
        DagDefinition dag = dagWith(step);

        assertThatThrownBy(() -> parser.parseAndValidate(dag))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("Invalid HTTP method");
    }

    @Test
    @DisplayName("HTTP_CALL to AWS metadata URL is blocked (SSRF protection)")
    void httpCall_awsMetadataUrl_throws() {
        StepDefinition step = step("s1", StepType.HTTP_CALL,
                Map.of("url", "http://169.254.169.254/latest/meta-data/"));
        DagDefinition dag = dagWith(step);

        assertThatThrownBy(() -> parser.parseAndValidate(dag))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("blocked for security");
    }

    @Test
    @DisplayName("HTTP_CALL with file:// URL is blocked")
    void httpCall_fileUrl_throws() {
        StepDefinition step = step("s1", StepType.HTTP_CALL,
                Map.of("url", "file:///etc/passwd"));
        DagDefinition dag = dagWith(step);

        assertThatThrownBy(() -> parser.parseAndValidate(dag))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("blocked for security");
    }

    // ── SCRIPT_EXEC Validation ────────────────────────────────────────────────

    @Test
    @DisplayName("Valid SCRIPT_EXEC step passes validation")
    void scriptExec_validConfig_passes() {
        StepDefinition step = step("s1", StepType.SCRIPT_EXEC, Map.of("script", "echo hello"));
        DagDefinition dag = dagWith(step);

        assertThatCode(() -> parser.parseAndValidate(dag)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SCRIPT_EXEC without script key throws DagValidationException")
    void scriptExec_missingScript_throws() {
        StepDefinition step = step("s1", StepType.SCRIPT_EXEC, Map.of());
        DagDefinition dag = dagWith(step);

        assertThatThrownBy(() -> parser.parseAndValidate(dag))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("Missing required config key 'script'");
    }

    @Test
    @DisplayName("SCRIPT_EXEC with script > 10KB throws DagValidationException")
    void scriptExec_oversizedScript_throws() {
        String hugeScript = "x".repeat(10_001);
        StepDefinition step = step("s1", StepType.SCRIPT_EXEC, Map.of("script", hugeScript));
        DagDefinition dag = dagWith(step);

        assertThatThrownBy(() -> parser.parseAndValidate(dag))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("exceeds 10KB");
    }

    // ── DELAY Validation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("DELAY with valid durationMs passes validation")
    void delay_validDuration_passes() {
        StepDefinition step = step("s1", StepType.DELAY, Map.of("durationMs", 5000));
        DagDefinition dag = dagWith(step);

        assertThatCode(() -> parser.parseAndValidate(dag)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DELAY with 0ms duration throws DagValidationException")
    void delay_zeroDuration_throws() {
        StepDefinition step = step("s1", StepType.DELAY, Map.of("durationMs", 0));
        DagDefinition dag = dagWith(step);

        assertThatThrownBy(() -> parser.parseAndValidate(dag))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("between 1ms and 1 hour");
    }

    @Test
    @DisplayName("DELAY exceeding 1 hour throws DagValidationException")
    void delay_exceedingOneHour_throws() {
        StepDefinition step = step("s1", StepType.DELAY, Map.of("durationMs", 3_600_001L));
        DagDefinition dag = dagWith(step);

        assertThatThrownBy(() -> parser.parseAndValidate(dag))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("between 1ms and 1 hour");
    }

    // ── CONDITIONAL_BRANCH Validation ────────────────────────────────────────

    @Test
    @DisplayName("Valid CONDITIONAL_BRANCH step passes validation")
    void conditionalBranch_validConfig_passes() {
        StepDefinition step = step("s1", StepType.CONDITIONAL_BRANCH, Map.of(
                "expression", "true",
                "trueStepId", "step2",
                "falseStepId", "step3"
        ));
        DagDefinition dag = dagWith(step);

        assertThatCode(() -> parser.parseAndValidate(dag)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CONDITIONAL_BRANCH missing trueStepId throws exception")
    void conditionalBranch_missingTrueStep_throws() {
        StepDefinition step = step("s1", StepType.CONDITIONAL_BRANCH, Map.of(
                "expression", "true",
                "falseStepId", "step3"
        ));
        DagDefinition dag = dagWith(step);

        assertThatThrownBy(() -> parser.parseAndValidate(dag))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("trueStepId");
    }

    // ── Retry Config Validation ───────────────────────────────────────────────

    @Test
    @DisplayName("Retry maxRetries > 10 throws DagValidationException")
    void retryConfig_maxRetriesExceeded_throws() {
        RetryConfig retry = RetryConfig.builder().maxRetries(11).build();
        StepDefinition step = StepDefinition.builder()
                .id("s1").name("Step 1").type(StepType.DELAY)
                .config(Map.of("durationMs", 100))
                .retryConfig(retry)
                .build();
        DagDefinition dag = dagWith(step);

        assertThatThrownBy(() -> parser.parseAndValidate(dag))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("maxRetries cannot exceed 10");
    }

    @Test
    @DisplayName("Retry maxRetries = 10 is valid")
    void retryConfig_maxRetries10_passes() {
        RetryConfig retry = RetryConfig.builder().maxRetries(10).build();
        StepDefinition step = StepDefinition.builder()
                .id("s1").name("Step 1").type(StepType.DELAY)
                .config(Map.of("durationMs", 100))
                .retryConfig(retry)
                .build();
        DagDefinition dag = dagWith(step);

        assertThatCode(() -> parser.parseAndValidate(dag)).doesNotThrowAnyException();
    }

    // ── Size Limits ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("DAG with exactly 200 steps passes max-step limit")
    void dagWithMaxSteps_passes() {
        List<StepDefinition> steps = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            steps.add(step("s" + i, StepType.DELAY, Map.of("durationMs", 100)));
        }
        DagDefinition dag = DagDefinition.builder().steps(steps).edges(List.of()).build();

        assertThatCode(() -> parser.parseAndValidate(dag)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DAG with 201 steps exceeds limit and throws")
    void dagWithTooManySteps_throws() {
        List<StepDefinition> steps = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            steps.add(step("s" + i, StepType.DELAY, Map.of("durationMs", 100)));
        }
        DagDefinition dag = DagDefinition.builder().steps(steps).edges(List.of()).build();

        assertThatThrownBy(() -> parser.parseAndValidate(dag))
                .isInstanceOf(DagValidationException.class)
                .hasMessageContaining("maximum step limit");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DagDefinition dagWith(StepDefinition step) {
        return DagDefinition.builder()
                .steps(List.of(step))
                .edges(List.of())
                .build();
    }

    private StepDefinition step(String id, StepType type, Map<String, Object> config) {
        return StepDefinition.builder()
                .id(id)
                .name("Step " + id)
                .type(type)
                .config(config)
                .build();
    }

    private StepDefinition httpStep(String id, String method, String url, String body) {
        Map<String, Object> config = new HashMap<>();
        config.put("url", url);
        config.put("method", method);
        if (body != null) config.put("body", body);
        return step(id, StepType.HTTP_CALL, config);
    }
}