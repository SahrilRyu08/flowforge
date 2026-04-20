package org.ryudev.com.flowforge.domain;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StepDefinition {
    private String id;
    private String name;
    private StepType type;
    private Map<String, Object> config;
    private RetryConfig retryConfig;
    private Integer timeoutSeconds;
}
