package org.ryudev.com.flowforge.repository;

import org.ryudev.com.flowforge.domain.WorkflowVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, UUID> {

    Optional<WorkflowVersion> findByWorkflow_IdAndVersion(UUID workflowId, Integer version);

    Page<WorkflowVersion> findByWorkflow_Id(UUID workflowId, Pageable pageable);

    @Query("SELECT COALESCE(MAX(v.version), 0) FROM WorkflowVersion v WHERE v.workflow.id = :workflowId")
    int findMaxVersionByWorkflowId(@Param("workflowId") UUID workflowId);
}
