package com.lanka.broker.service;

import com.lanka.broker.client.JobServiceClient;
import com.lanka.broker.client.NotificationClient;
import com.lanka.broker.dto.OfflineWorkerRequest;
import com.lanka.broker.model.Broker;
import com.lanka.broker.model.OfflineWorker;
import com.lanka.broker.repository.BrokerRepository;
import com.lanka.broker.repository.OfflineWorkerRepository;
import com.lanka.broker.security.AuthPrincipal;
import com.lanka.broker.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerServiceTest {

    @Mock
    private BrokerRepository brokers;
    @Mock
    private OfflineWorkerRepository workers;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwt;
    @Mock
    private NotificationClient notifications;
    @Mock
    private JobServiceClient jobService;

    private BrokerService service;

    @BeforeEach
    void setUp() {
        service = new BrokerService(brokers, workers, passwordEncoder, jwt, notifications, jobService,
                0.075, "admin@lanka.lk");
    }

    @Test
    void shouldReturnBrokerWhenBrokerExists() {
        Broker broker = approvedBroker(1L, "BRK-0001");
        when(brokers.findByBrokerId("BRK-0001")).thenReturn(Optional.of(broker));

        Broker result = service.requireBrokerAccess("BRK-0001", brokerPrincipal(1L));

        assertThat(result).isSameAs(broker);
    }

    @Test
    void shouldReturnNotFoundWhenBrokerDoesNotExist() {
        when(brokers.findByBrokerId("BRK-9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireBrokerAccess("BRK-9999", brokerPrincipal(1L)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void shouldRejectUnauthenticatedBrokerLookup() {
        assertThatThrownBy(() -> service.requireBrokerAccess("BRK-0001", null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(brokers, never()).findByBrokerId(any());
    }

    @Test
    void shouldForbidAccessToAnotherBrokerAccount() {
        Broker broker = approvedBroker(2L, "BRK-0002");
        when(brokers.findByBrokerId("BRK-0002")).thenReturn(Optional.of(broker));

        assertThatThrownBy(() -> service.requireBrokerAccess("BRK-0002", brokerPrincipal(1L)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void shouldReturnEmptyWorkerListWhenBrokerHasNoWorkers() {
        Broker broker = approvedBroker(1L, "BRK-0001");
        when(brokers.findByBrokerId("BRK-0001")).thenReturn(Optional.of(broker));
        when(jobService.placements(1L)).thenReturn(List.of());
        when(workers.findByBrokerEntityIdOrderByIdDesc(1L)).thenReturn(List.of());

        assertThat(service.getWorkers("BRK-0001", brokerPrincipal(1L))).isEmpty();
    }

    @Test
    void shouldReturnWorkersManagedByBroker() {
        Broker broker = approvedBroker(1L, "BRK-0001");
        OfflineWorker worker = offlineWorker(10L, 1L);
        when(brokers.findByBrokerId("BRK-0001")).thenReturn(Optional.of(broker));
        when(jobService.placements(1L)).thenReturn(List.of());
        when(workers.findByBrokerEntityIdOrderByIdDesc(1L)).thenReturn(List.of(worker));

        var result = service.getWorkers("BRK-0001", brokerPrincipal(1L));

        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.workerName()).isEqualTo("Nimal Silva");
            assertThat(response.status()).isEqualTo("ACTIVE");
        });
    }

    @Test
    void shouldReturnBadRequestForInvalidWorkerStatus() {
        Broker broker = approvedBroker(1L, "BRK-0001");
        OfflineWorker worker = offlineWorker(10L, 1L);
        when(brokers.findByBrokerId("BRK-0001")).thenReturn(Optional.of(broker));
        when(workers.findById(10L)).thenReturn(Optional.of(worker));

        assertThatThrownBy(() -> service.updateWorkerStatus(
                "BRK-0001", 10L, "UNKNOWN", brokerPrincipal(1L)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(workers, never()).save(any());
    }

    @Test
    void shouldRegisterWorkerUsingBrokerLocation() {
        Broker broker = approvedBroker(1L, "BRK-0001");
        when(brokers.findByBrokerId("BRK-0001")).thenReturn(Optional.of(broker));
        when(workers.save(any(OfflineWorker.class))).thenAnswer(invocation -> {
            OfflineWorker saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        when(workers.countByBrokerEntityId(1L)).thenReturn(1L);
        when(brokers.save(broker)).thenReturn(broker);

        var response = service.addOfflineWorker("BRK-0001",
                new OfflineWorkerRequest("Nimal Silva", "991234567V", "0771234567",
                        " Masonry, Painting, Masonry ", "Full Days"),
                brokerPrincipal(1L));

        ArgumentCaptor<OfflineWorker> workerCaptor = ArgumentCaptor.forClass(OfflineWorker.class);
        verify(workers).save(workerCaptor.capture());
        OfflineWorker saved = workerCaptor.getValue();
        assertThat(saved.getDistrict()).isEqualTo("Colombo");
        assertThat(saved.getCity()).isEqualTo("Colombo City");
        assertThat(saved.getSkills()).isEqualTo("Masonry,Painting");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.brokerId()).isEqualTo("BRK-0001");
    }

    private AuthPrincipal brokerPrincipal(Long id) {
        return new AuthPrincipal(id, "broker@lanka.lk", "Test Broker", "BROKER");
    }

    private Broker approvedBroker(Long id, String brokerId) {
        Broker broker = new Broker();
        broker.setId(id);
        broker.setBrokerId(brokerId);
        broker.setName("Test Broker");
        broker.setEmail("broker@lanka.lk");
        broker.setPhone("0770000000");
        broker.setDistrict("Colombo");
        broker.setCity("Colombo City");
        broker.setStatus("APPROVED");
        return broker;
    }

    private OfflineWorker offlineWorker(Long id, Long brokerEntityId) {
        OfflineWorker worker = new OfflineWorker();
        worker.setId(id);
        worker.setBrokerId("BRK-0001");
        worker.setBrokerEntityId(brokerEntityId);
        worker.setWorkerName("Nimal Silva");
        worker.setMobile("0771234567");
        worker.setDistrict("Colombo");
        worker.setCity("Colombo City");
        worker.setSkills("Masonry");
        worker.setStatus("ACTIVE");
        worker.setTotalJobs(0);
        worker.setCommissionEarned(0L);
        return worker;
    }
}
