package org.ryudev.com.flowforge.handler;

import lombok.extern.slf4j.Slf4j;
import org.ryudev.com.flowforge.domain.StepDefinition;
import org.ryudev.com.flowforge.domain.StepType;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
class ConditionalBranchHandler implements StepHandler {

    @Override
    public StepType supports() {
        return StepType.CONDITIONAL_BRANCH;
    }

    @Override
    public Object execute(StepDefinition step, Map<String, Object> previousOutputs) throws Exception {
        Map<String, Object> config = step.getConfig();
        String expression = (String) config.get("expression");
        String trueStepId = (String) config.get("trueStepId");
        String falseStepId = (String) config.get("falseStepId");

        // Simple expression evaluation: supports ${stepId.key} == "value"
        boolean result = evaluateExpression(expression, previousOutputs);
        String nextStep = result ? trueStepId : falseStepId;

        log.debug("Conditional '{}' evaluated to {} → routing to {}", expression, result, nextStep);
        return Map.of("result", result, "nextStep", nextStep);
    }

    private boolean evaluateExpression(String expression, Map<String, Object> outputs) {
        if ("true".equalsIgnoreCase(expression.trim())) return true;
        if ("false".equalsIgnoreCase(expression.trim())) return false;

        if (expression.contains("==")) {
            String[] parts = expression.split("==", 2);
            String leftStr = resolveValue(parts[0].trim(), outputs);
            String rightStr = parts[1].trim().replace("\"", "").replace("'", "").trim();
            return leftStr.equals(rightStr);
        }

        return false;
    }

    private String resolveValue(String expression, Map<String, Object> outputs) {
        if (expression.startsWith("${") && expression.endsWith("}")) {
            String path = expression.substring(2, expression.length() - 1);
            String[] parts = path.split("\\.", 2);
            if (parts.length == 2) {
                Object stepOutput = outputs.get(parts[0]);
                if (stepOutput instanceof Map<?, ?> map) {
                    Object val = map.get(parts[1]);
                    return val != null ? val.toString() : "";
                }
            }
        }
        return expression;
    }
}
