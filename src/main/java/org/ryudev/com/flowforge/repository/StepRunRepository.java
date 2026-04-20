package org.ryudev.com.flowforge.repository;

import org.ryudev.com.flowforge.domain.StepRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StepRunRepository extends JpaRepository<StepRun, UUID> {

    List<StepRun> findByWorkflowRun_IdOrderByCreatedAtAsc(UUID workflowRunId);

    Optional<StepRun> findFirstByWorkflowRun_IdAndStepIdOrderByCreatedAtDesc(UUID workflowRunId, String stepId);
}
