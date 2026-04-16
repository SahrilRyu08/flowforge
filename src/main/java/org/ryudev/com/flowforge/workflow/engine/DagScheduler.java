package org.ryudev.com.flowforge.workflow.engine;

import com.sun.source.doctree.SeeTree;
import org.ryudev.com.flowforge.workflow.model.Step;
import org.ryudev.com.flowforge.workflow.model.WorkflowDefinition;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DagScheduler {

    public List<List<Step>> computeExecutionLevels(WorkflowDefinition workflowDefinition) {
        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, Step> stepMap = new HashMap<>();

        for (Step s : workflowDefinition.steps()) {
            stepMap.put(s.id(), s);
            inDegree.put(s.id(), 0);
            adj.put(s.id(), new ArrayList<>());
        }

        for (Step s : workflowDefinition.steps()) {
            for (String dep : s.dependsOn()) {
                adj.get(dep).add(s.id());
                inDegree.put(s.id(), inDegree.get(s.id()) + 1);
            }
        }
        Deque<String> queue = inDegree.entrySet().stream()
                .filter(e-> e.getValue() == 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayDeque::new));
        List<List<Step>> levels = new ArrayList<>();
        while (!queue.isEmpty()) {
            int size = queue.size();
            List<Step> currentLevel = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                String id = queue.poll();
                currentLevel.add(stepMap.get(id));
                for (String next : adj.get(id)) {
                    int deg = inDegree.get(next) - 1;
                    inDegree.put(next, deg);
                    if (deg == 0) {
                        queue.add(next);
                    }
                }
            }
            levels.add(currentLevel);
        }
        if (levels.stream().mapToInt(List::size).sum() != workflowDefinition.steps().size())
            throw new IllegalArgumentException("Cyclic detected");

        return levels;
    }
}
