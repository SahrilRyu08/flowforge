package org.ryudev.com.flowforge.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ryudev.com.flowforge.domain.Role;
import org.ryudev.com.flowforge.domain.Tenant;
import org.ryudev.com.flowforge.domain.User;
import org.ryudev.com.flowforge.dto.request.LoginRequest;
import org.ryudev.com.flowforge.repository.TenantRepository;
import org.ryudev.com.flowforge.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Userv Controller Integration Test")
class UserControllerTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    private Tenant tenant;
    private User adminUser;
    private User viewerUser;
    private String adminToken;
    private String viewerToken;

    private static final String PASSWORD = "SecurePass123!";


    @BeforeEach
    void setUp() throws Exception {
        tenant = tenantRepository.save(Tenant.builder()
                .slug("user-test-corp")
                .name("user test corp")
                .build());

        adminUser = userRepository.save(User.builder()
                .tenant(tenant)
                .email("admin@usertest.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Admin User")
                .role(Role.ADMIN)
                .build());
        viewerUser = userRepository.save(User.builder()
                .tenant(tenant)
                .email("viewer@usertest.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Viewer User")
                .role(Role.ADMIN)
                .build());

        adminToken = loginAndGetToken("admin@usertest.com");
        viewerToken = loginAndGetToken("viewer@usertest.com");
    }

    @Test
    @DisplayName("GET /user -admin sees all users in tenant")
    void listUser_asAdmin_returnAll() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content",
                        hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.content[*].email",
                        hasItems("admin@usertest.com", "viewer@usertest.com")));
    }

    private String loginAndGetToken(String email) throws Exception {
        LoginRequest request = new LoginRequest();
        request.email = email;
        request.password = PASSWORD;
        request.tenantSlug = tenant.getSlug();

        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }
}