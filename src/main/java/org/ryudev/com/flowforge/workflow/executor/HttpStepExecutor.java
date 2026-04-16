package org.ryudev.com.flowforge.workflow.executor;

import org.ryudev.com.flowforge.workflow.model.Step;
import org.ryudev.com.flowforge.workflow.model.StepType;

public class HttpStepExecutor implements StepExecutor{
    @Override
    public StepType getType() {
        return StepType.HTTP;
    }

    @Override
    public StepExecutionResult execute(Step step) {
        return new StepExecutionResult(true,"{\"status\":200}", null);
    }
}
