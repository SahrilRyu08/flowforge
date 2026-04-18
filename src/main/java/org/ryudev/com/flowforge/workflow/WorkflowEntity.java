package org.ryudev.com.flowforge.workflow;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "workflow")
public record WorkflowEntity(
        @Id String id,
        String tenantId,
        String name,
        int version,
        String definitionJson,
        @ManyToOne WorkflowEntity previousVersion
) {


}
