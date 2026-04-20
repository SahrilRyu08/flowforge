package org.ryudev.com.flowforge.domain;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetryConfig {
    @Builder.Default
    private Integer maxRetries = 3;
    @Builder.Default
    private Long initialDelayMs = 1000L;
    @Builder.Default
    private Double backoffMultiplier = 2.0;
    @Builder.Default
    private Long maxDelayMs = 30000L;
}
