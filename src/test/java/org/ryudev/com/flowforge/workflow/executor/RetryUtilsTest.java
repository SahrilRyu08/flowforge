package org.ryudev.com.flowforge.workflow.executor;

import org.junit.jupiter.api.Test;
import org.ryudev.com.flowforge.workflow.model.Step;
import org.ryudev.com.flowforge.workflow.model.StepType;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RetryUtilsTest {
    @Test
    void retriesWithExponentBackoffAndFailsAfterMax() {
        Step step = new Step("fail", StepType.HTTP, Map.of(), List.of(), 2, Duration.ofMillis(50));
        StepExecutor failExec = mock(StepExecutor.class);
        when(failExec.execute(any())).thenThrow(new RuntimeException("Network Error"));

        StepExecutionResult stepExecutionResult = RetryUtils.executeWithRetry(failExec, step);
        assertFalse(stepExecutionResult.success());
        assertNotNull(stepExecutionResult.error());
        verify(failExec, times(3)).execute(step);
    }
}