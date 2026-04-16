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
        Set<String> stepIds = workflowDefinition.steps().stream().map(Step::id).collect(Collectors.toSet());
        for (Step step : workflowDefinition.steps()) {
            for (String dep : step.dependsOn()) {
                if(!stepIds.contains(dep)) {
                    throw new IllegalArgumentException("Step " + step.id() + "depends on:" + dep);
                }
            }
        }
        if (hasCycle(workflowDefinition.steps())) {
            throw new IllegalArgumentException("Workflow contains cyclic dependency");
        }
    }

    private boolean hasCycle(List<Step> steps) {
        Map<String, List<String>> adj = steps.stream().collect(Collectors.toMap(Step::id, Step::dependsOn));
        Set<String> visited = new HashSet<>();
        Set<String> recentStack = new HashSet<>();
        for (String id : adj.keySet()) {
            if (dfsCycle(id,adj, visited,recentStack)) return true;
        }
        return false;
    }

    private boolean dfsCycle(String id, Map<String, List<String>> adj, Set<String> visited, Set<String> recentStack) {
        visited.add(id);
        recentStack.add(id);

        for (String dep : adj.getOrDefault(id, List.of())) {
            if(!visited.contains(dep)) {
                if (dfsCycle(dep, adj, visited,recentStack)) {
                   return true;
                }
            }else if (recentStack.contains(dep)) return true;
        }
        recentStack.remove(id);
        return false;
    }
}
