package org.ryudev.com.flowforge.workflow;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowService {
    private final WorkflowRepository repository;

    public WorkflowEntity create(String tenantId, String name, String defJson) {
        WorkflowEntity workflowEntity = new WorkflowEntity(UUID.randomUUID().toString(), tenantId, name, 1, defJson, null);
        return repository.save(workflowEntity);
    }

    public WorkflowEntity rollback(String tenantId, String id, int targetVersion) {
       WorkflowEntity current = (WorkflowEntity) repository.findByIdAndTenantId(id,tenantId).orElseThrow();
       WorkflowEntity target  = (WorkflowEntity) repository.findByTenantIdAndNameAndVersion(tenantId,current.name(), targetVersion).orElseThrow();
        return repository.save(new WorkflowEntity(UUID.randomUUID().toString(), tenantId,
                target.name(), current.version()+1, target.definitionJson(), current));
    }

    public Page<WorkflowEntity> list(String tenantId, Pageable page) {
        return repository.findByTenantId(tenantId,page);
    }
}
