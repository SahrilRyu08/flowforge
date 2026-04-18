package org.ryudev.com.flowforge.workflow;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkflowRepository extends JpaRepository<WorkflowEntity, String> {
    Optional<Object> findByIdAndTenantId(String id, String tenantId);

    Optional<Object> findByTenantIdAndNameAndVersion(String tenantId, String name, int targetVersion);

    Page<WorkflowEntity> findByTenantId(String tenantId, Pageable page);
}
