package org.ryudev.com.flowforge.workflow.executor;

public record StepExecutionResult(
        boolean success, String output, Throwable error
) {
}
