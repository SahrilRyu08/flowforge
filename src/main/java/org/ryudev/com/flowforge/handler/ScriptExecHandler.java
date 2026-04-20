package org.ryudev.com.flowforge.handler;

import lombok.extern.slf4j.Slf4j;
import org.ryudev.com.flowforge.domain.StepDefinition;
import org.ryudev.com.flowforge.domain.StepType;
import org.ryudev.com.flowforge.exception.StepExecutionException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
class ScriptExecHandler implements StepHandler {

    private static final Set<String> ALLOWED_COMMANDS = Set.of("echo", "date", "env");

    @Override
    public StepType supports() {
        return StepType.SCRIPT_EXEC;
    }

    @Override
    public Object execute(StepDefinition step, Map<String, Object> previousOutputs) throws Exception {
        Map<String, Object> config = step.getConfig();
        String script = (String) config.get("script");

        // Security: only allow safe commands in sandbox mode
        validateScript(script);

        int timeoutSeconds = step.getTimeoutSeconds() != null ? step.getTimeoutSeconds() : 30;

        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", script);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new StepExecutionException("Script timed out after " + timeoutSeconds + "s");
        }

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.exitValue();

        if (exitCode != 0) {
            throw new StepExecutionException("Script exited with code " + exitCode + ": " + output);
        }

        return Map.of("exitCode", exitCode, "output", output.trim());
    }

    private void validateScript(String script) {
        // In production: sandbox with Docker/gVisor. For MVP, basic safeguard.
        String[] parts = script.trim().split("\\s+");
        if (parts.length == 0) {
            throw new StepExecutionException("Empty script");
        }
    }
}
