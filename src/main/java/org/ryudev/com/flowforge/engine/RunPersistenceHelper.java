package org.ryudev.com.flowforge.engine;

import lombok.RequiredArgsConstructor;
import org.ryudev.com.flowforge.domain.StepRun;
import org.ryudev.com.flowforge.domain.WorkflowRun;
import org.ryudev.com.flowforge.repository.StepRunRepository;
import org.ryudev.com.flowforge.repository.WorkflowRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists run/step state from async / parallel step threads using REQUIRES_NEW so each
 * flush succeeds even when the caller has no Spring transaction (e.g. ForkJoinPool).
 */
@Service
@RequiredArgsConstructor
public class RunPersistenceHelper {

    private final WorkflowRunRepository workflowRunRepository;
    private final StepRunRepository stepRunRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WorkflowRun saveRun(WorkflowRun run) {
        return workflowRunRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StepRun saveStep(StepRun step) {
        return stepRunRepository.save(step);
    }
}
