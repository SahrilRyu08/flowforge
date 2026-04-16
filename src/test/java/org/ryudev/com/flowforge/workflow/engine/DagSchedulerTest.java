package org.ryudev.com.flowforge.workflow.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ryudev.com.flowforge.workflow.model.Step;
import org.ryudev.com.flowforge.workflow.model.StepType;
import org.ryudev.com.flowforge.workflow.model.WorkflowDefinition;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DagSchedulerTest {

    @InjectMocks DagScheduler scheduler;
    @Test
    void groupsParallelStepCorrectly() {
        List<Step> steps = List.of(
                new Step("A", StepType.HTTP, Map.of(), List.of(), 0, Duration.ZERO),
                new Step("B", StepType.DELAY, Map.of("seconds", String.valueOf(2)), List.of(), 0, Duration.ZERO),
                new Step("C", StepType.SCRIPT, Map.of("cmd", "echo hi"), List.of("A", "B"), 0, Duration.ZERO)
        );

        WorkflowDefinition workflowDefinition = new WorkflowDefinition("wf", "Test", Duration.ofMinutes(5), steps);
        List<List<Step>> levels = scheduler.computeExecutionLevels(workflowDefinition);
        assertEquals(2, levels.size());
        assertThat(levels.get(0)).extracting(Step::id).containsExactlyInAnyOrder("A", "B");
        assertThat(levels.get(1)).extracting(Step::id).containsExactly("C");
    }
}