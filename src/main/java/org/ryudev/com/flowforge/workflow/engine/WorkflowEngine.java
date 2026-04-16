package org.ryudev.com.flowforge.workflow.engine;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.ryudev.com.flowforge.workflow.executor.RetryUtils;
import org.ryudev.com.flowforge.workflow.executor.StepExecutionResult;
import org.ryudev.com.flowforge.workflow.executor.StepExecutor;
import org.ryudev.com.flowforge.workflow.model.Step;
import org.ryudev.com.flowforge.workflow.model.StepType;
import org.ryudev.com.flowforge.workflow.model.WorkflowDefinition;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class WorkflowEngine {
    private final DagValidator validator;
    private final DagScheduler scheduler;
    public List<StepExecutor> executorList;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<StepType, StepExecutor> registry = new HashMap<>();

    @PostConstruct
    void initRegistry() {
        for (StepExecutor e : executorList) {
            registry.put(e.getType(), e);
        }
    }

    public WorkflowRunResult run(WorkflowDefinition workflowDefinition) throws Exception{
        validator.validate(workflowDefinition);

        List<List<Step>> levels = scheduler.computeExecutionLevels(workflowDefinition);

        CompletableFuture<?>[] levelFutures = levels.stream()
                .map(level -> CompletableFuture.runAsync(() -> {
                    List<CompletableFuture<StepExecutionResult>> futures = level.stream()
                            .map(step -> CompletableFuture.supplyAsync(() -> {
                                StepExecutor exec = registry.get(step.type());
                                return RetryUtils.executeWithRetry(exec, step);
                            }, executorService))
                            .toList();

                    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
                }, executorService))
                .toArray(CompletableFuture[]::new);

        CompletableFuture<Void> workflowFuture = CompletableFuture.allOf(levelFutures)
                .orTimeout(workflowDefinition.globalTimeout().toMillis(), TimeUnit.MILLISECONDS);

        try {
            workflowFuture.join();
            return new WorkflowRunResult(workflowDefinition.id(), "SUCCESS", System.currentTimeMillis());
        } catch (CompletionException e) {
            return new WorkflowRunResult(workflowDefinition.id(), "FAILED", System.currentTimeMillis(), e.getCause());
        }
    }

}

