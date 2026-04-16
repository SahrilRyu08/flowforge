package org.ryudev.com.flowforge.workflow.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ryudev.com.flowforge.workflow.executor.StepExecutionResult;
import org.ryudev.com.flowforge.workflow.executor.StepExecutor;
import org.ryudev.com.flowforge.workflow.model.Step;
import org.ryudev.com.flowforge.workflow.model.StepType;
import org.ryudev.com.flowforge.workflow.model.WorkflowDefinition;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineTest {
    @Mock
    StepExecutor httpExec;
    @Mock
    StepExecutor delayExec;
    @Mock
    DagValidator validator;
    @Mock
    DagScheduler scheduler;

    @InjectMocks
    WorkflowEngine engine;

    List<Step> steps;

    @BeforeEach
    void setUp() {
        when(httpExec.getType()).thenReturn(StepType.HTTP);
        when(delayExec.getType()).thenReturn(StepType.DELAY);

        engine.executorList = List.of(httpExec, delayExec);
        engine.initRegistry();

        doNothing().when(validator).validate(any());

        steps = List.of(
                new Step("A", StepType.HTTP, Map.of(), List.of(), 0, Duration.ZERO),
                new Step("B", StepType.DELAY, Map.of(), List.of(), 0, Duration.ZERO)
        );

        when(scheduler.computeExecutionLevels(any()))
                .thenReturn(List.of(steps));
    }

    @Test
    void executeParallelStepsAndReturnsSuccess() throws Exception {
        when(httpExec.execute(any())).thenReturn(new StepExecutionResult(true,"OK", null));
        when(delayExec.execute(any())).thenReturn(new StepExecutionResult(true,"OK", null));

        List<Step> steps = List.of(
                new Step("A", StepType.HTTP, Map.of(), List.of(), 0, Duration.ZERO),
                new Step("B",StepType.DELAY, Map.of(), List.of(), 0, Duration.ZERO)
        );
        WorkflowDefinition workflowDefinition = new WorkflowDefinition("wf1", "Test", Duration.ofSeconds(10),steps);
        WorkflowRunResult res = engine.run(workflowDefinition);
        assertEquals("SUCCESS", res.status());


        verify(httpExec, times(1)).execute(any());
        verify(delayExec, times(1)).execute(any());
    }

    @Test
    void timesOutIfWorflowExceedsGlobalLimit() throws Exception {
        when(httpExec.execute(any())).thenAnswer(invocation -> {
            Thread.sleep(5000);
            return new StepExecutionResult(true, "OK", null);
        });
        List<Step> steps = List.of(new Step("A",StepType.HTTP, Map.of(), List.of(), 0, Duration.ZERO));
        WorkflowDefinition workflowDefinition = new WorkflowDefinition("wf2", "Timout", Duration.ofMillis(200), steps);
        WorkflowRunResult res = engine.run(workflowDefinition);
        assertEquals("FAILED", res.status());
        assertNotNull(res.error());

    }
}