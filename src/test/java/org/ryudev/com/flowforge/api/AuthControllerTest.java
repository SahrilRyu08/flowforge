package org.ryudev.com.flowforge.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.ryudev.com.flowforge.domain.Role;
import org.ryudev.com.flowforge.domain.Tenant;
import org.ryudev.com.flowforge.domain.User;
import org.ryudev.com.flowforge.dto.request.LoginRequest;
import org.ryudev.com.flowforge.dto.request.RegisterRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Auth Controller Integration Tests")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Tenant testTenant;
    private User testUser;
    private static final String PASSWORD = "SecurePass123!";

    @BeforeEach
    void setUp() {
        testTenant = tenantRepository.save(Tenant.builder()
                .slug("test-corp")
                .name("Test Corporation")
                .build());

        testUser = userRepository.save(User.builder()
                .tenant(testTenant)
                .email("admin@test.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Admin User")
                .role(Role.ADMIN)
                .build());
    }

    // ── POST /api/v1/auth/login ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /login - valid credentials returns 200 with JWT tokens")
    void login_validCredentials_returns200WithTokens() throws Exception {
        LoginRequest request = new LoginRequest();
        request.email = testUser.getEmail();
        request.password = PASSWORD;
        request.tenantSlug = testTenant.getSlug();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.user.email").value(testUser.getEmail()))
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    @DisplayName("POST /login - wrong password returns 401")
    void login_wrongPassword_returns401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.email = testUser.getEmail();
        request.password = "wrongpassword";
        request.tenantSlug = testTenant.getSlug();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    @DisplayName("POST /login - unknown tenant returns 401")
    void login_unknownTenant_returns401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.email = testUser.getEmail();
        request.password = PASSWORD;
        request.tenantSlug = "nonexistent-tenant";

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /login - blank email returns 400 validation error")
    void login_blankEmail_returns400() throws Exception {
        LoginRequest request = new LoginRequest();
        request.email = "";
        request.password = PASSWORD;
        request.tenantSlug = testTenant.getSlug();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    @DisplayName("POST /login - invalid email format returns 400")
    void login_invalidEmailFormat_returns400() throws Exception {
        LoginRequest request = new LoginRequest();
        request.email = "not-an-email";
        request.password = PASSWORD;
        request.tenantSlug = testTenant.getSlug();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/v1/auth/register ────────────────────────────────────────────

    @Test
    @DisplayName("POST /register - valid request creates user and returns 201")
    void register_validRequest_returns201() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.email = "newuser@test.com";
        request.password = PASSWORD;
        request.fullName = "New User";
        request.tenantSlug = testTenant.getSlug();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("newuser@test.com"))
                .andExpect(jsonPath("$.user.role").value("VIEWER")); // default role
    }

    @Test
    @DisplayName("POST /register - duplicate email in same tenant returns 409")
    void register_duplicateEmail_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.email = testUser.getEmail(); // already exists
        request.password = PASSWORD;
        request.fullName = "Duplicate User";
        request.tenantSlug = testTenant.getSlug();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /register - password too short returns 400")
    void register_shortPassword_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.email = "shortpw@test.com";
        request.password = "123";
        request.fullName = "Test User";
        request.tenantSlug = testTenant.getSlug();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    // ── GET /api/v1/auth/me ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /me - with valid JWT returns current user")
    void me_withValidToken_returnsCurrentUser() throws Exception {
        String token = loginAndGetToken();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(testUser.getEmail()))
                .andExpect(jsonPath("$.tenantSlug").value(testTenant.getSlug()));
    }

    @Test
    @DisplayName("GET /me - without token returns 400")
    void me_withoutToken_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /me - with malformed token returns 400")
    void me_withMalformedToken_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/v1/auth/logout ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /logout - with valid token returns 204")
    void logout_withValidToken_returns204() throws Exception {
        String token = loginAndGetToken();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String loginAndGetToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.email = testUser.getEmail();
        request.password = PASSWORD;
        request.tenantSlug = testTenant.getSlug();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}