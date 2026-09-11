package com.lanka.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanka.user.model.User;
import com.lanka.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end coverage of the user-service HTTP API: registration, validation, the admin approval
 * workflow, login gating and the authorization rules that protect admin-only endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@gmail.com";
    private static final String ADMIN_PASSWORD = "admin123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository users;

    private Map<String, Object> workerPayload(String email) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "Kamal Perera");
        payload.put("mobile", "0771234567");
        payload.put("email", email);
        payload.put("password", "workerpass1");
        payload.put("role", "WORKER");
        payload.put("district", "Colombo");
        payload.put("city", "Colombo City");
        payload.put("nic", "991234567V");
        payload.put("skills", "Construction, Heavy Lifting");
        payload.put("availability", "Full Days");
        return payload;
    }

    private String adminToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("gmail", ADMIN_EMAIL, "password", ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String registerWorker(String email) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(workerPayload(email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        return email;
    }

    @Test
    void registerPersistsPendingUserWithHashedPasswordAndSkills() throws Exception {
        registerWorker("kamal@gmail.com");

        User saved = users.findByEmail("kamal@gmail.com").orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getRole()).isEqualTo("WORKER");
        assertThat(saved.getSkills()).isEqualTo("Construction,Heavy Lifting");
        assertThat(saved.getNic()).isEqualTo("991234567V");
        // Password must be hashed, never stored in clear text.
        assertThat(saved.getPassword()).startsWith("$2").isNotEqualTo("workerpass1");
    }

    @Test
    void registerRejectsDuplicateEmailWith409() throws Exception {
        registerWorker("dup@gmail.com");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(workerPayload("dup@gmail.com"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already exists")));
    }

    @Test
    void registerCannotEscalateToAdminRole() throws Exception {
        Map<String, Object> payload = workerPayload("hacker@gmail.com");
        payload.put("role", "ADMIN");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.role").exists());
        assertThat(users.findByEmail("hacker@gmail.com")).isEmpty();
    }

    @Test
    void registerReturnsFieldErrorsForInvalidPayload() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "");
        payload.put("email", "not-an-email");
        payload.put("password", "short");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.role").exists());
    }

    @Test
    void loginIsForbiddenUntilAdminApproves() throws Exception {
        registerWorker("pending@gmail.com");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "pending@gmail.com", "password", "workerpass1", "role", "WORKER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("approval")));
    }

    @Test
    void loginRejectsWrongPasswordAndWrongRole() throws Exception {
        registerWorker("kamal2@gmail.com");
        users.findByEmail("kamal2@gmail.com").ifPresent(user -> {
            user.setStatus("APPROVED");
            users.save(user);
        });

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "kamal2@gmail.com", "password", "wrongpass1", "role", "WORKER"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "kamal2@gmail.com", "password", "workerpass1", "role", "EMPLOYER"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminApprovalWorkflowGrantsLoginAndPersistsStatus() throws Exception {
        registerWorker("approve@gmail.com");
        Long id = users.findByEmail("approve@gmail.com").orElseThrow().getId();
        String token = adminToken();

        mockMvc.perform(get("/auth/users/pending").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'approve@gmail.com')]").exists());

        mockMvc.perform(put("/auth/users/" + id + "/approve").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(users.findById(id).orElseThrow().getStatus()).isEqualTo("APPROVED");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "approve@gmail.com", "password", "workerpass1", "role", "WORKER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("WORKER"))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.skills").value("Construction,Heavy Lifting"))
                .andExpect(jsonPath("$.city").value("Colombo City"));

        // Approving twice is a conflict, not a silent success.
        mockMvc.perform(put("/auth/users/" + id + "/approve").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void adminRejectionPersistsAndBlocksLogin() throws Exception {
        registerWorker("reject@gmail.com");
        Long id = users.findByEmail("reject@gmail.com").orElseThrow().getId();
        String token = adminToken();

        mockMvc.perform(put("/auth/users/" + id + "/reject").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(users.findById(id).orElseThrow().getStatus()).isEqualTo("REJECTED");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "reject@gmail.com", "password", "workerpass1", "role", "WORKER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void approvalEndpointsAreProtected() throws Exception {
        registerWorker("guard@gmail.com");
        Long id = users.findByEmail("guard@gmail.com").orElseThrow().getId();
        users.findById(id).ifPresent(user -> {
            user.setStatus("APPROVED");
            users.save(user);
        });
        String workerToken = objectMapper.readTree(mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of("email", "guard@gmail.com", "password", "workerpass1", "role", "WORKER"))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()).get("token").asText();

        // No token at all.
        mockMvc.perform(get("/auth/users/pending")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/auth/users/" + id + "/reject")).andExpect(status().isUnauthorized());
        // Valid token, wrong role.
        mockMvc.perform(get("/auth/users/pending").header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/auth/users/" + id + "/reject").header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/auth/stats").header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isForbidden());
        // A forged token is not accepted.
        mockMvc.perform(get("/auth/users/pending").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
        // The user was not modified.
        assertThat(users.findById(id).orElseThrow().getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void meReturnsTheAuthenticatedProfileAndStatsAreReal() throws Exception {
        registerWorker("me@gmail.com");
        String token = adminToken();
        Long id = users.findByEmail("me@gmail.com").orElseThrow().getId();
        mockMvc.perform(put("/auth/users/" + id + "/approve").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        String workerToken = objectMapper.readTree(mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of("email", "me@gmail.com", "password", "workerpass1", "role", "WORKER"))))
                        .andReturn().getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@gmail.com"))
                .andExpect(jsonPath("$.role").value("WORKER"))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/auth/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(users.count()))
                .andExpect(jsonPath("$.approvedUsers").value(users.countByStatus("APPROVED")))
                .andExpect(jsonPath("$.pendingUsers").value(users.countByStatus("PENDING")));
    }

    @Test
    void publicStatsReturnsApprovedWorkerCountWithoutAuthentication() throws Exception {
        registerWorker("public-stats@gmail.com");
        User worker = users.findByEmail("public-stats@gmail.com").orElseThrow();
        worker.setStatus("APPROVED");
        users.save(worker);

        mockMvc.perform(get("/auth/public-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvedWorkers")
                        .value(users.countByRoleAndStatus("WORKER", "APPROVED")));
    }

    @Test
    void unknownUserReturns404OnApproval() throws Exception {
        String token = adminToken();
        mockMvc.perform(put("/auth/users/999999/approve").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }
}
