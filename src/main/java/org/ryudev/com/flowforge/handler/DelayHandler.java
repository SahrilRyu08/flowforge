package org.ryudev.com.flowforge.handler;

import lombok.extern.slf4j.Slf4j;
import org.ryudev.com.flowforge.domain.StepDefinition;
import org.ryudev.com.flowforge.domain.StepType;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
class DelayHandler implements StepHandler {

    @Override
    public StepType supports() {
        return StepType.DELAY;
    }

    @Override
    public Object execute(StepDefinition step, Map<String, Object> previousOutputs) throws Exception {
        long durationMs = ((Number) step.getConfig().get("durationMs")).longValue();
        log.debug("Step {} delaying for {}ms", step.getId(), durationMs);
        Thread.sleep(durationMs);
        return Map.of("delayed", durationMs);
    }
}
