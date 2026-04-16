package org.ryudev.com.flowforge.workflow.executor;

import org.ryudev.com.flowforge.workflow.model.Step;

public class RetryUtils {
    public static StepExecutionResult executeWithRetry(StepExecutor executor, Step step) {
        Exception lastEx = null;
        for (int attempt = 0; attempt <= step.maxRetires(); attempt++) {
            try {
                return executor.execute(step);
            } catch (Exception e) {
                lastEx = e;
                if (attempt < step.maxRetires()) {
                    long delayMs = (long) (step.retryBackoff().toMillis() * Math.pow(2, attempt));
                    try { Thread.sleep(delayMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
        return new StepExecutionResult(false, null, lastEx);
    }
}