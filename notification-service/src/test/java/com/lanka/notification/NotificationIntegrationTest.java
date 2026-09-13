package com.lanka.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanka.notification.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notificationdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "notification.internal.token=test-internal-token",
        "jwt.secret=notification_test_secret_key_0123456789_abcdefghijklmnop",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class NotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwt;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldCreateSimulatedNotificationWithInternalToken() throws Exception {
        createNotification("worker@lanka.lk")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipient").value("worker@lanka.lk"))
                .andExpect(jsonPath("$.status").value("SIMULATED"))
                .andExpect(jsonPath("$.provider").value("SimulatedEmailProvider"));
    }

    @Test
    void shouldReturnUnauthorizedWhenCreatingWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notificationJson("worker@lanka.lk")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void shouldReturnBadRequestForInvalidNotification() throws Exception {
        mockMvc.perform(post("/notifications")
                        .header("X-Internal-Token", "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipient", "",
                                "channel", "PUSH",
                                "message", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.recipient").exists())
                .andExpect(jsonPath("$.fieldErrors.channel").exists())
                .andExpect(jsonPath("$.fieldErrors.message").exists());
    }

    @Test
    void shouldReturnNotificationsForAuthenticatedRecipient() throws Exception {
        createNotification("worker@lanka.lk").andExpect(status().isOk());
        String token = jwt.generateToken("worker@lanka.lk", "WORKER", 1L, "Test Worker");

        mockMvc.perform(get("/notifications/mine")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipient").value("worker@lanka.lk"));
    }

    @Test
    void shouldReturnForbiddenWhenReadingAnotherRecipientsNotifications() throws Exception {
        String token = jwt.generateToken("worker@lanka.lk", "WORKER", 1L, "Test Worker");

        mockMvc.perform(get("/notifications/recipient/other@lanka.lk")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    private org.springframework.test.web.servlet.ResultActions createNotification(String recipient) throws Exception {
        return mockMvc.perform(post("/notifications")
                .header("X-Internal-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(notificationJson(recipient)));
    }

    private String notificationJson(String recipient) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "recipient", recipient,
                "channel", "EMAIL",
                "type", "JOB_ACCEPTED",
                "subject", "Job update",
                "message", "Your application was accepted"));
    }
}
