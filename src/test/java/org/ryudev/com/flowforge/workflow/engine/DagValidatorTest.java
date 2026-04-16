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

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DagValidatorTest {
    @InjectMocks DagValidator validator;

    @Test
    void rejectCyclicDag() {
        List<Step> steps = List.of(
                new Step("A", StepType.HTTP, Map.of(), List.of("B"), 0, Duration.ZERO),
                new Step("B", StepType.HTTP, Map.of(), List.of("A"), 0, Duration.ZERO)
        );
        WorkflowDefinition workflowDefinition = new WorkflowDefinition("wf", "Cyclic", Duration.ofMinutes(5), steps);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(workflowDefinition));
    }

    @Test
    void acceptsAcyclicDaf() {
        List<Step> steps = List.of(
                new Step("A", StepType.HTTP, Map.of(), List.of(), 0, Duration.ZERO),
                new Step("B", StepType.HTTP, Map.of(), List.of("A"), 0, Duration.ZERO)
        );
        WorkflowDefinition workflowDefinition = new WorkflowDefinition("wf", "Cyclic", Duration.ofMinutes(5), steps);
        assertDoesNotThrow(() -> validator.validate(workflowDefinition));
    }
}