package org.ryudev.com.flowforge.workflow.security;

import org.junit.jupiter.api.Test;
import org.ryudev.com.flowforge.workflow.controller.TestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.mockito.Mockito.when;

@WebMvcTest(controllers = TestController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
@AutoConfigureMockMvc(addFilters = true)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
        "jwt.expiration-ms=3600000"
})
class JwtAuthenticationFilterTest {
    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    JwtService jwtService;

    @Test
    void validToken_allowsRequest() throws Exception {
        String token = "valid.token.here";

        when(jwtService.extractUsername(anyString())).thenReturn("tenant-A");
        when(jwtService.isValid(anyString(), anyString())).thenReturn(true);
        when(jwtService.extractRole(anyString())).thenReturn(Role.EDITOR);

        mockMvc.perform(get("/api/test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(jwtService).extractUsername(token);
        verify(jwtService).isValid(token, "tenant-A");
        verify(jwtService).extractRole(token);
    }
}