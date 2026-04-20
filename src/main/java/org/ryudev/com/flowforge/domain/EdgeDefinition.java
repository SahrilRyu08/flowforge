package org.ryudev.com.flowforge.domain;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EdgeDefinition {
    private String from;
    private String to;
    private String condition; // null = always, "success", "failure", "expression"
}
