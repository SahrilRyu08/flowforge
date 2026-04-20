package org.ryudev.com.flowforge.handler;



import org.ryudev.com.flowforge.domain.StepDefinition;
import org.ryudev.com.flowforge.domain.StepType;

import java.util.Map;

/** Strategy interface for executing a single step type */
public interface StepHandler {
    StepType supports();
    Object execute(StepDefinition step, Map<String, Object> previousOutputs) throws Exception;
}
