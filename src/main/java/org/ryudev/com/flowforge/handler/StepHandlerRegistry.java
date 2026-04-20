package org.ryudev.com.flowforge.handler;

import lombok.RequiredArgsConstructor;
import org.ryudev.com.flowforge.domain.StepType;
import org.ryudev.com.flowforge.exception.StepExecutionException;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class StepHandlerRegistry {

    private final List<StepHandler> handlers;
    private Map<StepType, StepHandler> handlerMap;

    @jakarta.annotation.PostConstruct
    public void init() {
        handlerMap = new EnumMap<>(StepType.class);
        for (StepHandler handler : handlers) {
            handlerMap.put(handler.supports(), handler);
        }
    }

    public StepHandler getHandler(StepType type) {
        StepHandler handler = handlerMap.get(type);
        if (handler == null) {
            throw new StepExecutionException("No handler registered for step type: " + type);
        }
        return handler;
    }
}
