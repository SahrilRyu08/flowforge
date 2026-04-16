package org.ryudev.com.flowforge.workflow.executor;

import org.ryudev.com.flowforge.workflow.model.Step;
import org.ryudev.com.flowforge.workflow.model.StepType;

public interface StepExecutor {
    StepType getType();
    StepExecutionResult execute(Step step);
}
