package org.ryudev.com.flowforge.workflow.engine;

public record WorkflowRunResult(String workflowId, String status, long finishedAt, Throwable error) {
    public WorkflowRunResult(String id, String status, long finishedAt) {
        this(id,status,finishedAt,null);
    }
}
