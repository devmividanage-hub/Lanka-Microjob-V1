package com.lanka.broker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:brokerdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed.demo-data=false",
        "notification.client.enabled=false",
        "jwt.secret=broker_service_test_secret_key_0123456789_abcdefghijklmnop",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class BrokerApplicationContextTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldReturnPublicBrokerStatistics() throws Exception {
        mockMvc.perform(get("/brokers/public-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvedBrokers").value(0))
                .andExpect(jsonPath("$.totalOfflineWorkers").value(0));
    }

    @Test
    void shouldReturnUnauthorizedForProtectedBrokerWorkers() throws Exception {
        mockMvc.perform(get("/brokers/BRK-0001/workers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
