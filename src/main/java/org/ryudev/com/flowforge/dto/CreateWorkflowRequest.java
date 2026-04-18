package org.ryudev.com.flowforge.dto;

public record CreateWorkflowRequest(
        String name,
        String definitionJson
) {
}
