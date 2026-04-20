package org.ryudev.com.flowforge.repository;

import org.ryudev.com.flowforge.domain.RunStatus;
import org.ryudev.com.flowforge.domain.WorkflowRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, UUID> {

    @Query("""
            SELECT r FROM WorkflowRun r
            JOIN FETCH r.tenant
            JOIN FETCH r.workflow
            JOIN FETCH r.workflowVersion
            WHERE r.id = :id
            """)
    Optional<WorkflowRun> findFetchedById(@Param("id") UUID id);

    @Query("""
            SELECT r FROM WorkflowRun r
            WHERE r.tenant.id = :tenantId
              AND (:workflowId IS NULL OR r.workflow.id = :workflowId)
              AND (:status IS NULL OR r.status = :status)
            """)
    Page<WorkflowRun> findByTenantWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("workflowId") UUID workflowId,
            @Param("status") RunStatus status,
            Pageable pageable);

    Optional<WorkflowRun> findByIdAndTenant_Id(UUID id, UUID tenantId);

    long countByTenant_IdAndStatus(UUID tenantId, RunStatus status);

    @Query("""
            SELECT COUNT(r) FROM WorkflowRun r
            WHERE r.tenant.id = :tenantId
              AND r.status = :status
              AND r.createdAt >= :since
            """)
    long countByTenantAndStatusSince(
            @Param("tenantId") UUID tenantId,
            @Param("status") RunStatus status,
            @Param("since") Instant since);

    @Query("""
            SELECT COALESCE(AVG(r.durationMs), 0) FROM WorkflowRun r
            WHERE r.tenant.id = :tenantId
              AND r.status = org.ryudev.com.flowforge.domain.RunStatus.SUCCESS
              AND r.durationMs IS NOT NULL
              AND r.createdAt >= :since
            """)
    double averageDurationMsSince(@Param("tenantId") UUID tenantId, @Param("since") Instant since);

    @Query("""
            SELECT COUNT(r) FROM WorkflowRun r
            WHERE r.tenant.id = :tenantId AND r.createdAt >= :since
            """)
    long countByTenantSince(@Param("tenantId") UUID tenantId, @Param("since") Instant since);
}
