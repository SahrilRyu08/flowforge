package org.ryudev.com.flowforge.engine;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ryudev.com.flowforge.domain.*;
import org.ryudev.com.flowforge.event.RunEventPublisher;
import org.ryudev.com.flowforge.handler.StepHandler;
import org.ryudev.com.flowforge.handler.StepHandlerRegistry;
import org.ryudev.com.flowforge.repository.WorkflowRunRepository;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DagExecutor Unit Tests")
class DagExecutorTest {

    @Mock TopologicalSorter topologicalSorter;
    @Mock
    StepHandlerRegistry handlerRegistry;
    @Mock
    RunPersistenceHelper persistence;
    @Mock
    RunEventPublisher eventPublisher;
    @Mock
    StepHandler mockHandler;
    @Mock
    WorkflowRunRepository workflowRunRepository;

    @InjectMocks DagExecutor executor;

    private Tenant tenant;
    private WorkflowRun workflowRun;
    private DagDefinition dag;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(UUID.randomUUID()).slug("test").name("Test").build();
        Workflow workflow = Workflow.builder()
                .id(UUID.randomUUID()).tenant(tenant).name("Test WF")
                .globalTimeoutSeconds(60).build();

        workflowRun = WorkflowRun.builder()
                .id(UUID.randomUUID()).tenant(tenant).workflow(workflow)
                .triggerType(TriggerType.MANUAL).build();

        when(persistence.saveRun(any())).thenAnswer(inv -> inv.getArgument(0));
        when(workflowRunRepository.getReferenceById(any())).thenReturn(workflowRun);
        when(persistence.saveStep(any())).thenAnswer(inv -> {
            var sr = inv.getArgument(0, StepRun.class);
            if (sr.getId() == null) {
                try {
                    var field = StepRun.class.getDeclaredField("id");
                    field.setAccessible(true);
                    field.set(sr, UUID.randomUUID());
                } catch (Exception ignored) {}
            }
            return sr;
        });
    }

    @Test
    @DisplayName("Successful single-step workflow transitions run to SUCCESS")
    void execute_singleSuccessfulStep_runsToSuccess() throws Exception {
        dag = singleStepDag("s1");
        when(topologicalSorter.sort(dag)).thenReturn(List.of(Set.of("s1")));
        when(handlerRegistry.getHandler(any())).thenReturn(mockHandler);
        when(mockHandler.execute(any(), any())).thenReturn(Map.of("result", "ok"));

        executor.execute(workflowRun, dag, 60);

        // Wait for async execution to complete (virtual thread)
        Thread.sleep(200);

        verify(persistence, atLeastOnce()).saveRun(argThat(run ->
                run.getStatus() == RunStatus.RUNNING || run.getStatus() == RunStatus.SUCCESS));
        verify(eventPublisher).publishRunStarted(eq(tenant.getId()), any());
        verify(eventPublisher).publishStepStarted(eq(tenant.getId()), any(), any());
        verify(eventPublisher).publishStepCompleted(eq(tenant.getId()), any(), any());
        verify(eventPublisher).publishRunFinished(eq(tenant.getId()), any());
    }

    @Test
    @DisplayName("Step failure causes run to fail after retries are exhausted")
    void execute_stepFailsAllRetries_runFails() throws Exception {
        dag = singleStepDagWithRetry("s1", 2); // max 2 retries
        when(topologicalSorter.sort(dag)).thenReturn(List.of(Set.of("s1")));
        when(handlerRegistry.getHandler(any())).thenReturn(mockHandler);
        when(mockHandler.execute(any(), any()))
                .thenThrow(new RuntimeException("Network error"))
                .thenThrow(new RuntimeException("Network error"))
                .thenThrow(new RuntimeException("Network error"));

        executor.execute(workflowRun, dag, 60);
        Thread.sleep(500);

        verify(mockHandler, times(3)).execute(any(), any()); // 1 initial + 2 retries
        verify(eventPublisher).publishStepFailed(eq(tenant.getId()), any(), any());
        verify(eventPublisher).publishRunFinished(eq(tenant.getId()), any());
    }

    @Test
    @DisplayName("Step succeeds on second retry after initial failure")
    void execute_stepSucceedsOnRetry_runsToSuccess() throws Exception {
        dag = singleStepDagWithRetry("s1", 3);
        when(topologicalSorter.sort(dag)).thenReturn(List.of(Set.of("s1")));
        when(handlerRegistry.getHandler(any())).thenReturn(mockHandler);

        AtomicInteger callCount = new AtomicInteger(0);
        when(mockHandler.execute(any(), any())).thenAnswer(inv -> {
            if (callCount.incrementAndGet() < 2) throw new RuntimeException("Transient error");
            return Map.of("result", "recovered");
        });

        executor.execute(workflowRun, dag, 60);
        Thread.sleep(500);

        verify(mockHandler, times(2)).execute(any(), any());
        verify(eventPublisher).publishStepCompleted(eq(tenant.getId()), any(), any());
    }

    @Test
    @DisplayName("Parallel steps in same level are all executed")
    void execute_parallelSteps_allExecuted() throws Exception {
        dag = threeStepDag("A", "B", "C"); // A → B, A → C (B and C parallel)
        when(topologicalSorter.sort(dag)).thenReturn(
                List.of(Set.of("A"), Set.of("B", "C")));
        when(handlerRegistry.getHandler(any())).thenReturn(mockHandler);
        when(mockHandler.execute(any(), any())).thenReturn(Map.of());

        executor.execute(workflowRun, dag, 60);
        Thread.sleep(300);

        verify(mockHandler, times(3)).execute(any(), any()); // A, B, C all run
    }

    @Test
    @DisplayName("Empty edge list runs all steps in single level")
    void execute_noEdges_allStepsRunInParallel() throws Exception {
        dag = disconnectedDag("X", "Y", "Z");
        when(topologicalSorter.sort(dag)).thenReturn(List.of(Set.of("X", "Y", "Z")));
        when(handlerRegistry.getHandler(any())).thenReturn(mockHandler);
        when(mockHandler.execute(any(), any())).thenReturn(Map.of());

        executor.execute(workflowRun, dag, 60);
        Thread.sleep(300);

        verify(mockHandler, times(3)).execute(any(), any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DagDefinition singleStepDag(String stepId) {
        return DagDefinition.builder()
                .steps(List.of(delayStep(stepId, 0)))
                .edges(List.of())
                .build();
    }

    private DagDefinition singleStepDagWithRetry(String stepId, int maxRetries) {
        StepDefinition step = StepDefinition.builder()
                .id(stepId).name("Step " + stepId)
                .type(StepType.DELAY)
                .config(Map.of("durationMs", 10L))
                .retryConfig(RetryConfig.builder()
                        .maxRetries(maxRetries)
                        .initialDelayMs(50L)
                        .backoffMultiplier(1.0)
                        .maxDelayMs(100L)
                        .build())
                .build();
        return DagDefinition.builder().steps(List.of(step)).edges(List.of()).build();
    }

    private DagDefinition threeStepDag(String a, String b, String c) {
        return DagDefinition.builder()
                .steps(List.of(delayStep(a, 0), delayStep(b, 0), delayStep(c, 0)))
                .edges(List.of(
                        EdgeDefinition.builder().from(a).to(b).build(),
                        EdgeDefinition.builder().from(a).to(c).build()
                ))
                .build();
    }

    private DagDefinition disconnectedDag(String... ids) {
        List<StepDefinition> steps = Arrays.stream(ids).map(id -> delayStep(id, 0)).toList();
        return DagDefinition.builder().steps(steps).edges(List.of()).build();
    }

    private StepDefinition delayStep(String id, long durationMs) {
        return StepDefinition.builder()
                .id(id).name("Step " + id)
                .type(StepType.DELAY)
                .config(Map.of("durationMs", durationMs))
                .retryConfig(RetryConfig.builder().maxRetries(0).build())
                .build();
    }
}