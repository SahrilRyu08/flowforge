package org.ryudev.com.flowforge.repository;

import org.ryudev.com.flowforge.domain.Workflow;
import org.ryudev.com.flowforge.domain.WorkflowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    @Query("""
        SELECT w FROM Workflow w
        WHERE w.tenant.id = :tenantId
          AND (:status IS NULL OR w.status = :status)
          AND (:search IS NULL OR LOWER(w.name) LIKE LOWER(CONCAT('%', :search, '%')))
          AND w.status <> org.ryudev.com.flowforge.domain.WorkflowStatus.ARCHIVED
        """)
    Page<Workflow> findByTenantWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("status") WorkflowStatus status,
            @Param("search") String search,
            Pageable pageable);

    Optional<Workflow> findByIdAndTenant_Id(UUID id, UUID tenantId);

    Optional<Workflow> findByWebhookToken(String webhookToken);

    @Query("SELECT COUNT(w) FROM Workflow w WHERE w.tenant.id = :tenantId AND w.status = org.ryudev.com.flowforge.domain.WorkflowStatus.ACTIVE")
    Long countActiveByTenantId(@Param("tenantId") UUID tenantId);

    @Query("""
            SELECT w FROM Workflow w JOIN FETCH w.tenant
            WHERE w.cronExpression IS NOT NULL
              AND w.status = org.ryudev.com.flowforge.domain.WorkflowStatus.ACTIVE
            """)
    java.util.List<Workflow> findAllWithActiveCron();
}
