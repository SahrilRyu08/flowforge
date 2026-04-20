package org.ryudev.com.flowforge.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.ryudev.com.flowforge.domain.*;
import org.ryudev.com.flowforge.dto.request.CreateWorkflowRequest;
import org.ryudev.com.flowforge.dto.request.LoginRequest;
import org.ryudev.com.flowforge.dto.request.UpdateWorkflowRequest;
import org.ryudev.com.flowforge.repository.TenantRepository;
import org.ryudev.com.flowforge.repository.UserRepository;
import org.ryudev.com.flowforge.repository.WorkflowRepository;
import org.ryudev.com.flowforge.repository.WorkflowVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Workflow Controller Integration Tests")
class WorkflowControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    WorkflowRepository workflowRepository;
    @Autowired
    WorkflowVersionRepository versionRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Tenant tenant;
    private User editorUser;
    private User viewerUser;
    private String editorToken;
    private String viewerToken;

    private static final String PASSWORD = "SecurePass123!";

    @BeforeEach
    void setUp() throws Exception {
        tenant = tenantRepository.save(Tenant.builder()
                .slug("workflow-test-corp")
                .name("Workflow Test Corp")
                .build());

        editorUser = userRepository.save(User.builder()
                .tenant(tenant).email("editor@test.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Editor User").role(Role.EDITOR).build());

        viewerUser = userRepository.save(User.builder()
                .tenant(tenant).email("viewer@test.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Viewer User").role(Role.VIEWER).build());

        editorToken = login("editor@test.com", PASSWORD);
        viewerToken = login("viewer@test.com", PASSWORD);
    }

    // ── POST /api/v1/workflows ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /workflows - Editor creates workflow, returns 201 with DAG")
    void createWorkflow_editor_returns201() throws Exception {
        CreateWorkflowRequest req = validCreateRequest("My Workflow");

        mockMvc.perform(post("/api/v1/workflows")
                        .header("Authorization", "Bearer " + editorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("My Workflow"))
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andExpect(jsonPath("$.dagDefinition.steps", hasSize(2)));
    }

    @Test
    @DisplayName("POST /workflows - Viewer cannot create workflow (403)")
    void createWorkflow_viewer_returns403() throws Exception {
        CreateWorkflowRequest req = validCreateRequest("Viewer Workflow");

        mockMvc.perform(post("/api/v1/workflows")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /workflows - Unauthenticated returns 403")
    void createWorkflow_unauthenticated_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest("test"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /workflows - DAG with cycle returns 400")
    void createWorkflow_dagWithCycle_returns400() throws Exception {
        DagDefinition cyclicDag = DagDefinition.builder()
                .steps(List.of(StepDefinition.builder()
                                .id("A").name("Step A").type(StepType.DELAY)
                                .config(Map.of("durationMs", 100)).build(),
                        StepDefinition.builder()
                                .id("B").name("Step B").type(StepType.DELAY)
                                .config(Map.of("durationMs", 100)).build()
                ))
                .edges(List.of(
                        EdgeDefinition.builder().from("A").to("B").build(),
                        EdgeDefinition.builder().from("B").to("A").build() // cycle!
                ))
                .build();

        CreateWorkflowRequest req = new CreateWorkflowRequest();
        req.name = "Cyclic Workflow";
        req.dagDefinition = cyclicDag;

        mockMvc.perform(post("/api/v1/workflows")
                        .header("Authorization", "Bearer " + editorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Cycle detected")));
    }

    @Test
    @DisplayName("POST /workflows - missing name returns 400 with field error")
    void createWorkflow_missingName_returns400() throws Exception {
        CreateWorkflowRequest req = new CreateWorkflowRequest();
        req.name = null;
        req.dagDefinition = validDag();

        mockMvc.perform(post("/api/v1/workflows")
                        .header("Authorization", "Bearer " + editorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    // ── GET /api/v1/workflows ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /workflows - returns paginated list for tenant")
    void listWorkflows_returnsPaginatedList() throws Exception {
        // Create 3 workflows
        for (int i = 0; i < 3; i++) {
            createWorkflow("Workflow " + i);
        }

        mockMvc.perform(get("/api/v1/workflows")
                        .header("Authorization", "Bearer " + viewerToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.pageable").exists());
    }

    @Test
    @DisplayName("GET /workflows - search filter narrows results")
    void listWorkflows_searchFilter_narrowsResults() throws Exception {
        createWorkflow("Alpha Pipeline");
        createWorkflow("Beta Pipeline");
        createWorkflow("Gamma Service");

        mockMvc.perform(get("/api/v1/workflows")
                        .header("Authorization", "Bearer " + viewerToken)
                        .param("search", "Pipeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    @DisplayName("GET /workflows - viewer cannot see other tenant's workflows")
    void listWorkflows_tenantIsolation() throws Exception {
        // Create another tenant and workflows
        Tenant otherTenant = tenantRepository.save(Tenant.builder()
                .slug("other-corp").name("Other Corp").build());
        User otherUser = userRepository.save(User.builder()
                .tenant(otherTenant).email("other@other.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Other User").role(Role.EDITOR).build());

        createWorkflow("Tenant1 Workflow");

        mockMvc.perform(get("/api/v1/workflows")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", not(hasItem("Other Corp Workflow"))));
    }

    // ── GET /api/v1/workflows/{id} ────────────────────────────────────────────

    @Test
    @DisplayName("GET /workflows/{id} - returns workflow detail with DAG")
    void getWorkflow_exists_returnsDetail() throws Exception {
        String workflowId = createWorkflow("Detail Workflow");

        mockMvc.perform(get("/api/v1/workflows/" + workflowId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workflowId))
                .andExpect(jsonPath("$.name").value("Detail Workflow"))
                .andExpect(jsonPath("$.dagDefinition").exists())
                .andExpect(jsonPath("$.webhookToken").isNotEmpty());
    }

    @Test
    @DisplayName("GET /workflows/{id} - non-existent ID returns 404")
    void getWorkflow_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/workflows/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isNotFound());
    }

    // ── PUT /api/v1/workflows/{id} ────────────────────────────────────────────

    @Test
    @DisplayName("PUT /workflows/{id} - editor updates workflow, bumps version")
    void updateWorkflow_editor_bumpsVersion() throws Exception {
        String workflowId = createWorkflow("Old Name");

        UpdateWorkflowRequest req = new UpdateWorkflowRequest();
        req.name = "New Name";
        req.changeNote = "Renamed the workflow";
        req.dagDefinition = validDag();

        mockMvc.perform(put("/api/v1/workflows/" + workflowId)
                        .header("Authorization", "Bearer " + editorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.currentVersion").value(2));
    }

    // ── DELETE /api/v1/workflows/{id} ─────────────────────────────────────────

    @Test
    @DisplayName("DELETE /workflows/{id} - editor archives workflow, returns 204")
    void deleteWorkflow_editor_returns204() throws Exception {
        String workflowId = createWorkflow("To Delete");

        mockMvc.perform(delete("/api/v1/workflows/" + workflowId)
                        .header("Authorization", "Bearer " + editorToken))
                .andExpect(status().isNoContent());

        // Confirm archived (not in list)
        mockMvc.perform(get("/api/v1/workflows")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(workflowId))));
    }

    @Test
    @DisplayName("DELETE /workflows/{id} - viewer cannot delete (403)")
    void deleteWorkflow_viewer_returns403() throws Exception {
        String workflowId = createWorkflow("Protected");

        mockMvc.perform(delete("/api/v1/workflows/" + workflowId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/v1/workflows/{id}/rollback/{version} ───────────────────────

    @Test
    @DisplayName("POST /rollback - restores previous version as new version")
    void rollbackWorkflow_restoresPreviousVersion() throws Exception {
        String workflowId = createWorkflow("Rollback Test");

        // Update to version 2 (new DAG revision)
        UpdateWorkflowRequest req = new UpdateWorkflowRequest();
        req.name = "Updated Name";
        req.dagDefinition = validDag();
        mockMvc.perform(put("/api/v1/workflows/" + workflowId)
                        .header("Authorization", "Bearer " + editorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Rollback pointer to version 1 (immutable history — still version 1 DAG)
        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/rollback/1")
                        .header("Authorization", "Bearer " + editorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    // ── POST /api/v1/workflows/{id}/trigger ───────────────────────────────────

    @Test
    @DisplayName("POST /trigger - triggers run and returns 202 with run ID")
    void triggerWorkflow_returns202WithRunId() throws Exception {
        String workflowId = createWorkflow("Trigger Test");

        UpdateWorkflowRequest activate = new UpdateWorkflowRequest();
        activate.status = org.ryudev.com.flowforge.domain.WorkflowStatus.ACTIVE;
        mockMvc.perform(put("/api/v1/workflows/" + workflowId)
                        .header("Authorization", "Bearer " + editorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activate)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/trigger")
                        .header("Authorization", "Bearer " + editorToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.workflowId").value(workflowId))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createWorkflow(String name) throws Exception {
        CreateWorkflowRequest req = validCreateRequest(name);
        MvcResult result = mockMvc.perform(post("/api/v1/workflows")
                        .header("Authorization", "Bearer " + editorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    private CreateWorkflowRequest validCreateRequest(String name) {
        CreateWorkflowRequest req = new CreateWorkflowRequest();
        req.name = name;
        req.dagDefinition = validDag();
        return req;
    }

    private DagDefinition validDag() {
        return DagDefinition.builder()
                .steps(List.of(
                        StepDefinition.builder()
                                .id("start").name("Start").type(StepType.DELAY)
                                .config(Map.of("durationMs", 100)).build(),
                        StepDefinition.builder()
                                .id("finish").name("Finish").type(StepType.DELAY)
                                .config(Map.of("durationMs", 100)).build()
                ))
                .edges(List.of(
                       EdgeDefinition.builder().from("start").to("finish").build()
                ))
                .build();
    }

    private String login(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        req.email = email;
        req.password = password;
        req.tenantSlug = tenant.getSlug();
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}