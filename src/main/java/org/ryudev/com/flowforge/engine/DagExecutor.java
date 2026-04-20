package org.ryudev.com.flowforge.engine;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ryudev.com.flowforge.domain.*;
import org.ryudev.com.flowforge.event.RunEventPublisher;
import org.ryudev.com.flowforge.handler.StepHandlerRegistry;
import org.ryudev.com.flowforge.repository.WorkflowRunRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Core DAG execution engine.
 * Executes steps level-by-level from topological sort, parallelizing
 * steps within the same level using virtual threads (Java 21).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DagExecutor {

    private final TopologicalSorter topologicalSorter;
    private final StepHandlerRegistry handlerRegistry;
    private final RunPersistenceHelper persistence;
    private final RunEventPublisher eventPublisher;
    private final WorkflowRunRepository workflowRunRepository;

    /**
     * @param globalTimeoutSeconds from {@link org.ryudev.com.flowforge.domain.Workflow} while still loaded
     *                             in a transactional boundary — avoids lazy proxy access on the async thread.
     */
    @Async("workflowExecutor")
    public void execute(WorkflowRun workflowRun, DagDefinition dag, int globalTimeoutSeconds) {
        log.info("Starting workflow run: {}", workflowRun.getId());

        int timeoutSeconds = globalTimeoutSeconds > 0 ? globalTimeoutSeconds : 3600;
        UUID tenantId = workflowRun.getTenant().getId();

        workflowRun.setStatus(RunStatus.RUNNING);
        workflowRun.setStartedAt(Instant.now());
        final WorkflowRun runRef = persistence.saveRun(workflowRun);
        eventPublisher.publishRunStarted(tenantId, runRef);
        Map<String, StepDefinition> stepMap = buildStepMap(dag);
        Map<String, Object> stepOutputs = new ConcurrentHashMap<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<RunStatus> runFuture = executor.submit(() ->
                    executeWithTimeout(tenantId, runRef, dag, stepMap, stepOutputs));

            RunStatus finalStatus;
            try {
                finalStatus = runFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                runFuture.cancel(true);
                finalStatus = RunStatus.TIMED_OUT;
                log.warn("Workflow run {} timed out after {}s", workflowRun.getId(), timeoutSeconds);
            }

            finishRun(tenantId, runRef, finalStatus, null);
        } catch (Exception e) {
            log.error("Workflow run {} failed with exception", runRef.getId(), e);
            finishRun(tenantId, runRef, RunStatus.FAILED, e.getMessage());
        }
    }

    private RunStatus executeWithTimeout(
            UUID tenantId,
            WorkflowRun workflowRun,
            DagDefinition dag,
            Map<String, StepDefinition> stepMap,
            Map<String, Object> stepOutputs) throws Exception {

        List<Set<String>> levels = topologicalSorter.sort(dag);
        Map<String, StepStatus> stepStatuses = new ConcurrentHashMap<>();

        for (Set<String> level : levels) {
            Set<String> runnableSteps = filterRunnableSteps(level, stepStatuses, dag);

            if (runnableSteps.isEmpty()) {
                log.debug("Skipping level — no runnable steps (all gated by conditions)");
                continue;
            }

            // Execute all steps in this level in parallel
            List<CompletableFuture<StepResult>> futures = runnableSteps.stream()
                    .map(stepId -> executeStep(tenantId, workflowRun, stepMap.get(stepId), stepOutputs))
                    .toList();

            // Wait for all parallel steps to finish
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Collect results
            for (CompletableFuture<StepResult> future : futures) {
                StepResult result = future.get();
                stepStatuses.put(result.stepId(), result.status());
                if (result.output() != null) {
                    stepOutputs.put(result.stepId(), result.output());
                }
            }

            // If any critical step failed, abort
            boolean hasCriticalFailure = runnableSteps.stream()
                    .anyMatch(id -> stepStatuses.get(id) == StepStatus.FAILED);

            if (hasCriticalFailure) {
                log.warn("Workflow run {} aborting due to step failure", workflowRun.getId());
                return RunStatus.FAILED;
            }
        }

        return RunStatus.SUCCESS;
    }

    private CompletableFuture<StepResult> executeStep(
            UUID tenantId,
            WorkflowRun workflowRun,
            StepDefinition stepDef,
            Map<String, Object> previousOutputs) {

        return CompletableFuture.supplyAsync(() -> {
            StepRun stepRun = createStepRun(workflowRun, stepDef);
            eventPublisher.publishStepStarted(tenantId, workflowRun, stepRun);

            var handler = handlerRegistry.getHandler(stepDef.getType());
            int maxRetries = getMaxRetries(stepDef);
            long delayMs = getInitialDelay(stepDef);
            double multiplier = getBackoffMultiplier(stepDef);

            for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
                stepRun.setAttemptNumber(attempt);
                stepRun.setStatus(attempt == 1 ? StepStatus.RUNNING : StepStatus.RETRYING);
                stepRun.setStartedAt(Instant.now());
                stepRun = persistence.saveStep(stepRun);

                try {
                    Object output = handler.execute(stepDef, previousOutputs);
                    finishStepRun(stepRun, StepStatus.SUCCESS, output, null);
                    eventPublisher.publishStepCompleted(tenantId, workflowRun, stepRun);
                    return new StepResult(stepDef.getId(), StepStatus.SUCCESS, output);

                } catch (Exception e) {
                    log.warn("Step {} attempt {}/{} failed: {}",
                            stepDef.getId(), attempt, maxRetries + 1, e.getMessage());

                    if (attempt <= maxRetries) {
                        try {
                            Thread.sleep(Math.min(delayMs, getMaxDelay(stepDef)));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        delayMs = (long) (delayMs * multiplier);
                    } else {
                        finishStepRun(stepRun, StepStatus.FAILED, null, e.getMessage());
                        eventPublisher.publishStepFailed(tenantId, workflowRun, stepRun);
                        return new StepResult(stepDef.getId(), StepStatus.FAILED, null);
                    }
                }
            }

            return new StepResult(stepDef.getId(), StepStatus.FAILED, null);
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Set<String> filterRunnableSteps(
            Set<String> level,
            Map<String, StepStatus> stepStatuses,
            DagDefinition dag) {

        return level.stream()
                .filter(stepId -> {
                    // Find all incoming edges for this step
                    List<EdgeDefinition> incomingEdges = dag.getEdges() == null
                            ? List.of()
                            : dag.getEdges().stream()
                              .filter(e -> e.getTo().equals(stepId))
                              .toList();

                    // If no incoming edges, always run
                    if (incomingEdges.isEmpty()) return true;

                    // Check all parent conditions are satisfied
                    return incomingEdges.stream().allMatch(edge -> {
                        StepStatus parentStatus = stepStatuses.get(edge.getFrom());
                        if (parentStatus == null) return false;
                        if (edge.getCondition() == null) return parentStatus == StepStatus.SUCCESS;
                        return switch (edge.getCondition()) {
                            case "success" -> parentStatus == StepStatus.SUCCESS;
                            case "failure" -> parentStatus == StepStatus.FAILED;
                            case "always"  -> true;
                            default        -> parentStatus == StepStatus.SUCCESS;
                        };
                    });
                })
                .collect(Collectors.toSet());
    }

    private Map<String, StepDefinition> buildStepMap(DagDefinition dag) {
        Map<String, StepDefinition> map = new HashMap<>();
        for (StepDefinition step : dag.getSteps()) {
            map.put(step.getId(), step);
        }
        return map;
    }

    private StepRun createStepRun(WorkflowRun workflowRun, StepDefinition stepDef) {
        WorkflowRun runRef = workflowRunRepository.getReferenceById(workflowRun.getId());
        return persistence.saveStep(StepRun.builder()
                .workflowRun(runRef)
                .stepId(stepDef.getId())
                .stepName(stepDef.getName())
                .status(StepStatus.PENDING)
                .build());
    }

    private void finishStepRun(StepRun stepRun, StepStatus status, Object output, String error) {
        Instant now = Instant.now();
        stepRun.setStatus(status);
        stepRun.setFinishedAt(now);
        stepRun.setOutput(output);
        stepRun.setErrorMessage(error);
        if (stepRun.getStartedAt() != null) {
            stepRun.setDurationMs(now.toEpochMilli() - stepRun.getStartedAt().toEpochMilli());
        }
        persistence.saveStep(stepRun);
    }

    private void finishRun(UUID tenantId, WorkflowRun run, RunStatus status, String errorMessage) {
        Instant now = Instant.now();
        run.setStatus(status);
        run.setFinishedAt(now);
        run.setErrorMessage(errorMessage);
        if (run.getStartedAt() != null) {
            run.setDurationMs(now.toEpochMilli() - run.getStartedAt().toEpochMilli());
        }
        persistence.saveRun(run);
        eventPublisher.publishRunFinished(tenantId, run);
        log.info("Workflow run {} finished with status {}", run.getId(), status);
    }

    private int getMaxRetries(StepDefinition step) {
        return step.getRetryConfig() != null && step.getRetryConfig().getMaxRetries() != null
                ? step.getRetryConfig().getMaxRetries() : 3;
    }

    private long getInitialDelay(StepDefinition step) {
        return step.getRetryConfig() != null && step.getRetryConfig().getInitialDelayMs() != null
                ? step.getRetryConfig().getInitialDelayMs() : 1000L;
    }

    private double getBackoffMultiplier(StepDefinition step) {
        return step.getRetryConfig() != null && step.getRetryConfig().getBackoffMultiplier() != null
                ? step.getRetryConfig().getBackoffMultiplier() : 2.0;
    }

    private long getMaxDelay(StepDefinition step) {
        return step.getRetryConfig() != null && step.getRetryConfig().getMaxDelayMs() != null
                ? step.getRetryConfig().getMaxDelayMs() : 30000L;
    }
    private record StepResult(String stepId, StepStatus status, Object output) {}
}
