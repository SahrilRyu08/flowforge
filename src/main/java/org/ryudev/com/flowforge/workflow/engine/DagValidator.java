package org.ryudev.com.flowforge.workflow.engine;

import org.ryudev.com.flowforge.workflow.model.Step;
import org.ryudev.com.flowforge.workflow.model.WorkflowDefinition;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DagValidator {
    public void validate(WorkflowDefinition workflowDefinition) {
        Set<String> collect = workflowDefinition.steps().stream().map(Step::id).collect(Collectors.toSet());
        for (Step step : workflowDefinition.steps()) {
            for (String s : step.dependsOn()) {
                if (!collect.contains(s)) {
                    throw new IllegalArgumentException("Step " + step.id() + " dependsOn " + s);
                }
            }
        }
        if (hasCycle(workflowDefinition.steps())) {
            throw new IllegalArgumentException("workflow contains cyclic dependency");
        }

    }

    private boolean hasCycle(List<Step> steps) {
        Map<String, List<String>> adj = steps.stream().collect(Collectors.toMap(Step::id, Step::dependsOn));
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();
        for (String id : adj.keySet()) {
            if(dfsCycle(id,adj, visited, recStack)) return true;
        }
        return false;
    }

    private boolean dfsCycle(String id, Map<String, List<String>> adj, Set<String> visited, Set<String> recStack) {
        visited.add(id);
        recStack.add(id);
        for (String dep : adj.getOrDefault(id, List.of())) {
            if(!visited.contains(dep)) {
                if (dfsCycle(dep, adj, visited, recStack))
                    return true;
            } else if (recStack.contains(dep)) {
                return true;
            }
        }
        recStack.remove(id);
        return false;

    }
}
