package com.onesignal.platform.iam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesignal.platform.common.security.Argon2PasswordService;
import com.onesignal.platform.iam.api.dto.AuthDto;
import com.onesignal.platform.iam.api.dto.MemberDto;
import com.onesignal.platform.iam.domain.User;
import com.onesignal.platform.iam.infrastructure.InvitationRepository;
import com.onesignal.platform.iam.infrastructure.OrganizationRepository;
import com.onesignal.platform.iam.infrastructure.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private Argon2PasswordService passwordEncoder;

    @BeforeEach
    void setupAdminUser() {
        // Ensure the seeded admin user exists and has a known hash for Password123!
        User admin = userRepository.findByEmail("admin@pushnotify.local").orElse(null);
        if (admin != null) {
            admin.setPasswordHash(passwordEncoder.encode("Password123!"));
            userRepository.save(admin);
        }
    }

    @Test
    @DisplayName("End-to-End Auth: Login, Get Profile, Token Refresh, and Logout")
    void endToEnd_authFlow() throws Exception {
        // 1. Login with admin credentials
        AuthDto.LoginRequest loginRequest = new AuthDto.LoginRequest();
        loginRequest.setEmail("admin@pushnotify.local");
        loginRequest.setPassword("Password123!");

        MvcResult loginResult = mockMvc.perform(post("/admin/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("admin@pushnotify.local"))
                .andExpect(jsonPath("$.user.superAdmin").value(true))
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        String accessToken = json.get("accessToken").asText();

        Cookie refreshCookie = loginResult.getResponse().getCookie("push_refresh_token");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getValue()).isNotEmpty();

        // 2. Access /admin/api/v1/auth/me using Bearer token
        mockMvc.perform(get("/admin/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@pushnotify.local"))
                .andExpect(jsonPath("$.organizations").isArray());

        // 3. Access Organizations endpoint using Bearer token
        mockMvc.perform(get("/admin/api/v1/organizations")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 4. Token Refresh using HttpOnly Cookie
        MvcResult refreshResult = mockMvc.perform(post("/admin/api/v1/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        Cookie newRefreshCookie = refreshResult.getResponse().getCookie("push_refresh_token");
        assertThat(newRefreshCookie).isNotNull();
        assertThat(newRefreshCookie.getValue()).isNotEqualTo(refreshCookie.getValue());

        // 5. Logout
        mockMvc.perform(post("/admin/api/v1/auth/logout")
                        .cookie(newRefreshCookie))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Organization Member Invitation & Acceptance Workflow")
    void memberInvitation_and_acceptanceFlow() throws Exception {
        // 1. Login as admin
        AuthDto.LoginRequest loginRequest = new AuthDto.LoginRequest();
        loginRequest.setEmail("admin@pushnotify.local");
        loginRequest.setPassword("Password123!");

        MvcResult loginResult = mockMvc.perform(post("/admin/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();

        UUID acmeOrgId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID devRoleId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        // 2. Issue invitation for a new developer
        String testEmail = "dev-" + UUID.randomUUID().toString().substring(0, 8) + "@company.com";
        MemberDto.InviteMemberRequest inviteReq = new MemberDto.InviteMemberRequest();
        inviteReq.setEmail(testEmail);
        inviteReq.setRoleId(devRoleId);

        MvcResult inviteResult = mockMvc.perform(post("/admin/api/v1/organizations/" + acmeOrgId + "/invitations")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(testEmail))
                .andExpect(jsonPath("$.token").isString())
                .andReturn();

        // 3. Retrieve raw token returned from invitation endpoint
        String inviteToken = objectMapper.readTree(inviteResult.getResponse().getContentAsString()).get("token").asText();
        assertThat(inviteToken).isNotBlank();

        // 4. Accept invitation using the returned token
        MemberDto.AcceptInviteRequest acceptReq = new MemberDto.AcceptInviteRequest();
        acceptReq.setToken(inviteToken);
        acceptReq.setFullName("Jane Developer");
        acceptReq.setPassword("Password123!");

        mockMvc.perform(post("/admin/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptReq)))
                .andExpect(status().isOk());

        // 5. Verify the newly registered user can now authenticate
        AuthDto.LoginRequest devLogin = new AuthDto.LoginRequest();
        devLogin.setEmail(testEmail);
        devLogin.setPassword("Password123!");

        mockMvc.perform(post("/admin/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(devLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.email").value(testEmail));
    }
}
