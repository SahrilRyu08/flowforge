package org.ryudev.com.flowforge.workflow.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.runtime.ObjectMethods;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WrokflowModelTest {
    @Test
    void parsesValidationflowJson() throws JsonProcessingException {
        String json = """
                {
                    "id" : "wf-1", "name" : "Test", "globalTimeout": "PT1M",
                    "steps" : [
                        { "id":"A", "type": "HTTP", "config": { "url":"http://api"}, "dependsOn":[], "maxRetires":2, "retryBackoff": "PT2S"},
                        {"id":"B", "type": "DELAY", "config": { "seconds":5}, "dependsOn": ["A"], "maxRetires":0, "retryBackoff": "PT0S"}
                    ]
                }
                """;

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        WorkflowDefinition wrokflowDefinition = mapper.readValue(json, WorkflowDefinition.class);
        assertEquals(2, wrokflowDefinition.steps().size());
        assertEquals("A", wrokflowDefinition.steps().get(0).id());
        assertEquals(60, wrokflowDefinition.globalTimeout().getSeconds());
    }

}