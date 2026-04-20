package org.ryudev.com.flowforge.domain;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DagDefinition {
    private List<StepDefinition> steps;
    private List<EdgeDefinition> edges;
}
