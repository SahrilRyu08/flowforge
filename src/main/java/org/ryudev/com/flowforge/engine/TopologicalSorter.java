package org.ryudev.com.flowforge.engine;



import org.ryudev.com.flowforge.domain.DagDefinition;
import org.ryudev.com.flowforge.domain.EdgeDefinition;
import org.ryudev.com.flowforge.domain.StepDefinition;
import org.ryudev.com.flowforge.exception.DagValidationException;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Validates a DAG and produces a topologically-sorted execution order.
 * Uses Kahn's algorithm (BFS-based) for O(V+E) complexity.
 */
@Component
public class TopologicalSorter {

    /**
     * Validates the DAG and returns steps in valid execution order.
     * Detects: duplicate step IDs, missing references, cycles.
     *
     * @param dag the workflow DAG definition
     * @return ordered list of step IDs (parallel groups preserved as sets)
     */
    public List<Set<String>> sort(DagDefinition dag) {
        validate(dag);

        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, Set<String>> adjacency = new HashMap<>();
        Map<String, Set<String>> reverseAdjacency = new HashMap<>();

        // Initialize maps for all steps
        for (StepDefinition step : dag.getSteps()) {
            inDegree.put(step.getId(), 0);
            adjacency.put(step.getId(), new LinkedHashSet<>());
            reverseAdjacency.put(step.getId(), new LinkedHashSet<>());
        }

        // Build adjacency and compute in-degrees
        List<EdgeDefinition> edges = dag.getEdges() != null ? dag.getEdges() : List.of();
        for (EdgeDefinition edge : edges) {
            adjacency.get(edge.getFrom()).add(edge.getTo());
            reverseAdjacency.get(edge.getTo()).add(edge.getFrom());
            inDegree.merge(edge.getTo(), 1, Integer::sum);
        }

        // Kahn's algorithm — collect parallel groups
        List<Set<String>> executionLevels = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();

        inDegree.entrySet().stream()
                .filter(e -> e.getValue() == 0)
                .map(Map.Entry::getKey)
                .forEach(queue::offer);

        int processedCount = 0;
        while (!queue.isEmpty()) {
            Set<String> currentLevel = new LinkedHashSet<>();

            int levelSize = queue.size();
            for (int i = 0; i < levelSize; i++) {
                String stepId = queue.poll();
                currentLevel.add(stepId);
                processedCount++;

                for (String successor : adjacency.get(stepId)) {
                    inDegree.merge(successor, -1, Integer::sum);
                    if (inDegree.get(successor) == 0) {
                        queue.offer(successor);
                    }
                }
            }
            executionLevels.add(currentLevel);
        }

        if (processedCount != dag.getSteps().size()) {
            Set<String> cycleNodes = findCycleNodes(inDegree);
            throw new DagValidationException(
                    "Cycle detected in DAG. Involved steps: " + cycleNodes);
        }

        return executionLevels;
    }

    /**
     * Validates structural integrity of the DAG.
     */
    public void validate(DagDefinition dag) {
        if (dag == null) {
            throw new DagValidationException("DAG definition cannot be null");
        }
        if (dag.getSteps() == null || dag.getSteps().isEmpty()) {
            throw new DagValidationException("DAG must contain at least one step");
        }

        Set<String> stepIds = new HashSet<>();
        for (StepDefinition step : dag.getSteps()) {
            if (step.getId() == null || step.getId().isBlank()) {
                throw new DagValidationException("Step ID cannot be null or blank");
            }
            if (!stepIds.add(step.getId())) {
                throw new DagValidationException(
                        "Duplicate step ID: " + step.getId());
            }
            if (step.getType() == null) {
                throw new DagValidationException(
                        "Step type cannot be null for step: " + step.getId());
            }
        }

        if (dag.getEdges() != null) {
            for (EdgeDefinition edge : dag.getEdges()) {
                if (!stepIds.contains(edge.getFrom())) {
                    throw new DagValidationException(
                            "Edge references unknown step (from): " + edge.getFrom());
                }
                if (!stepIds.contains(edge.getTo())) {
                    throw new DagValidationException(
                            "Edge references unknown step (to): " + edge.getTo());
                }
                if (edge.getFrom().equals(edge.getTo())) {
                    throw new DagValidationException(
                            "Self-loop detected on step: " + edge.getFrom());
                }
            }
        }
    }

    private Set<String> findCycleNodes(Map<String, Integer> inDegree) {
        Set<String> cycleNodes = new HashSet<>();
        inDegree.forEach((stepId, degree) -> {
            if (degree > 0) cycleNodes.add(stepId);
        });
        return cycleNodes;
    }
}
