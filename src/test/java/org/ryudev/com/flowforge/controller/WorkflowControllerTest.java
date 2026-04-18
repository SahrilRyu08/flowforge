package org.ryudev.com.flowforge.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.ryudev.com.flowforge.tenant.TenantContext;
import org.ryudev.com.flowforge.workflow.WorkflowEntity;
import org.ryudev.com.flowforge.workflow.WorkflowService;
import org.ryudev.com.flowforge.workflow.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(WorkflowController.class)
@Import(WorkflowControllerTest.TestSecurityConfig.class)
class WorkflowControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private WorkflowService service;
    @MockitoBean private JwtService jwtService;

    private static final String TENANT_ID = "tenant-abc";

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST /api/workflows - creates workflow with ROLE_EDITOR")
    void createsWorkflow_withActiveSecurity() throws Exception {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_EDITOR"));
        var authentication = new UsernamePasswordAuthenticationToken(
                TENANT_ID, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        given(service.create(eq(TENANT_ID), eq("Test"), anyString()))
                .willReturn(new WorkflowEntity("wf-1", TENANT_ID, "Test", 1, "{}", null));

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(TENANT_ID);

            mvc.perform(post("/api/workflows")
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Test\",\"definitionJson\":\"{}\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("wf-1"));
        }

        verify(service).create(eq(TENANT_ID), eq("Test"), anyString());
    }

    @Test
    @DisplayName("GET /api/workflows - returns paginated listed for tenant with ROLE_VIEWER")
    void listWorkflows_withPagination_andViewerRole() throws Exception {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_VIEWER"));
        var authentication = new UsernamePasswordAuthenticationToken(
                TENANT_ID, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        WorkflowEntity wf1 = new WorkflowEntity("wf-1", TENANT_ID, "Workflow A", 1, "{}", null);
        WorkflowEntity wf2 = new WorkflowEntity("wf-2", TENANT_ID, "Workflow B", 3, "{}", null);
        Page<WorkflowEntity> page = new PageImpl<>(List.of(wf1, wf2), PageRequest.of(0, 10), 2);

        given(service.list(eq(TENANT_ID), any(Pageable.class))).willReturn(page);

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(TENANT_ID);

            mvc.perform(get("/api/workflows")
                            .header("X-Tenant-ID", TENANT_ID)
                            .param("page", "0")
                            .param("size", "10")
                            .param("sort", "name,asc")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].id").value("wf-1"))
                    .andExpect(jsonPath("$.content[1].id").value("wf-2"))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.number").value(0))
                    .andExpect(jsonPath("$.size").value(10));
        }

        verify(service).list(eq(TENANT_ID), argThat(p ->
                p.getPageNumber() == 0 &&
                        p.getPageSize() == 10 &&
                        p.getSort().stream().anyMatch(s -> s.getProperty().equals("name"))
        ));
    }

    @Test
    @DisplayName("GET /api/workflows - returns 403 for unauthenticated user")
    void listWorkflows_rejectedForUnauthenticated() throws Exception {
        mvc.perform(get("/api/workflows")
                        .param("page", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/workflows/{id}/rollback/{version} - rolls back to previous version with ROLE_EDITOR")
    void rollbackWorkflow_withEditorRole_success() throws Exception {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_EDITOR"));
        var authentication = new UsernamePasswordAuthenticationToken(
                TENANT_ID, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String workflowId = "wf-existing";
        int targetVersion = 2;

        WorkflowEntity current = new WorkflowEntity(
                workflowId, TENANT_ID, "My Workflow", 5,
                "{\"id\":\"wf-existing\",\"steps\":[...]}", null
        );
        WorkflowEntity target = new WorkflowEntity(
                "wf-v2-snapshot", TENANT_ID, "My Workflow", 2,
                "{\"id\":\"wf-existing\",\"steps\":[...]}", null
        );
        WorkflowEntity rolledBack = new WorkflowEntity(
                "wf-new-v6", TENANT_ID, "My Workflow", 6    ,
                target.definitionJson(), current
        );

        given(service.rollback(eq(TENANT_ID), eq(workflowId), eq(targetVersion)))
                .willReturn(rolledBack);

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(TENANT_ID);

            mvc.perform(post("/api/workflows/{id}/rollback/{version}", workflowId, targetVersion)
                            .header("X-Tenant-ID", TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("wf-new-v6"))
                    .andExpect(jsonPath("$.version").value(6))
                    .andExpect(jsonPath("$.previousVersion.id").value(workflowId))
                    .andExpect(jsonPath("$.name").value("My Workflow"));
        }

        verify(service).rollback(eq(TENANT_ID), eq(workflowId), eq(targetVersion));
    }


    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/auth/**", "/swagger-ui/**","/v3/api-docs", "/actuator/**").permitAll()
                            .requestMatchers("/api/workflows/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_EDITOR", "ROLE_VIEWER")
                            .requestMatchers("/api/triggers/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_EDITOR")
                            .anyRequest().authenticated())
                    .build();
        }
    }
}