package org.ryudev.com.flowforge.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ryudev.com.flowforge.domain.*;
import org.ryudev.com.flowforge.dto.request.CreateWorkflowRequest;
import org.ryudev.com.flowforge.dto.request.LoginRequest;
import org.ryudev.com.flowforge.dto.request.UpdateWorkflowRequest;
import org.ryudev.com.flowforge.repository.TenantRepository;
import org.ryudev.com.flowforge.repository.UserRepository;
import org.ryudev.com.flowforge.repository.WorkflowRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.ryudev.com.flowforge.FlowforgeApplication;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end style test: auth → create workflow → activate → trigger → run row exists.
 */
@SpringBootTest(classes = FlowforgeApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowE2ETest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired WorkflowRunRepository workflowRunRepository;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .slug("e2e-tenant")
                .name("E2E Tenant")
                .build());
        userRepository.save(User.builder()
                .tenant(tenant)
                .email("e2e@flowforge.local")
                .passwordHash(passwordEncoder.encode("E2EPass123!"))
                .fullName("E2E User")
                .role(Role.EDITOR)
                .build());

        LoginRequest login = new LoginRequest();
        login.email = "e2e@flowforge.local";
        login.password = "E2EPass123!";
        login.tenantSlug = "e2e-tenant";
        var res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        token = objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    void createActivateTrigger_persistsRun() throws Exception {
        DagDefinition dag = DagDefinition.builder()
                .steps(List.of(
                        StepDefinition.builder()
                                .id("s1")
                                .name("quick")
                                .type(StepType.DELAY)
                                .config(Map.of("durationMs", 10))
                                .build()
                ))
                .edges(List.of())
                .build();

        CreateWorkflowRequest create = new CreateWorkflowRequest();
        create.name = "E2E Workflow";
        create.dagDefinition = dag;

        String wid = objectMapper.readTree(mockMvc.perform(post("/api/v1/workflows")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(create)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id").asText();

        UpdateWorkflowRequest activate = new UpdateWorkflowRequest();
        activate.status = WorkflowStatus.ACTIVE;
        mockMvc.perform(put("/api/v1/workflows/" + wid)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activate)))
                .andExpect(status().isOk());

        String runId = objectMapper.readTree(mockMvc.perform(post("/api/v1/workflows/" + wid + "/trigger")
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isAccepted())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id").asText();

        assertThat(workflowRunRepository.findById(UUID.fromString(runId))).isPresent();
    }
}
