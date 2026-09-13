package com.lanka.notification.service;

import com.lanka.notification.dto.NotificationRequest;
import com.lanka.notification.model.Notification;
import com.lanka.notification.provider.DeliveryResult;
import com.lanka.notification.provider.NotificationProvider;
import com.lanka.notification.provider.NotificationProviderRegistry;
import com.lanka.notification.repository.NotificationRepository;
import com.lanka.notification.security.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notifications;
    @Mock
    private NotificationProviderRegistry providers;
    @Mock
    private NotificationProvider provider;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notifications, providers);
    }

    @Test
    void shouldCreateNotificationSuccessfully() {
        when(notifications.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(providers.forChannel("EMAIL")).thenReturn(provider);
        when(provider.deliver("worker@lanka.lk", "GENERAL", "A job was assigned"))
                .thenReturn(new DeliveryResult(true, "SimulatedEmailProvider", "SIMULATED", "Logged only"));

        var response = service.send(new NotificationRequest(
                " worker@lanka.lk ", null, null, null, " A job was assigned "));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.recipient()).isEqualTo("worker@lanka.lk");
        assertThat(response.channel()).isEqualTo("EMAIL");
        assertThat(response.type()).isEqualTo("GENERAL");
        assertThat(response.status()).isEqualTo("SIMULATED");
        assertThat(response.provider()).isEqualTo("SimulatedEmailProvider");
        assertThat(response.sentAt()).isNotNull();
    }

    @Test
    void shouldStoreFailedStatusWhenProviderThrows() {
        when(notifications.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(providers.forChannel("SMS")).thenReturn(provider);
        when(provider.name()).thenReturn("BrokenProvider");
        when(provider.deliver("0771234567", "ALERT", "Test message"))
                .thenThrow(new IllegalStateException("provider unavailable"));

        var response = service.send(new NotificationRequest(
                "0771234567", "SMS", "alert", null, "Test message"));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.provider()).isEqualTo("BrokenProvider");
        assertThat(response.sentAt()).isNull();
    }

    @Test
    void shouldReturnNotificationsNewestFirstFromRepository() {
        Notification newest = notification(2L, "worker@lanka.lk", "SECOND");
        Notification oldest = notification(1L, "worker@lanka.lk", "FIRST");
        when(notifications.findAllByOrderByIdDesc()).thenReturn(List.of(newest, oldest));

        assertThat(service.list()).extracting(response -> response.id()).containsExactly(2L, 1L);
    }

    @Test
    void shouldReturnNotificationsForAuthenticatedRecipient() {
        when(notifications.findTop100ByRecipientOrderByIdDesc("worker@lanka.lk"))
                .thenReturn(List.of(notification(1L, "worker@lanka.lk", "JOB_ACCEPTED")));

        var result = service.listMine(user("worker@lanka.lk"));

        assertThat(result).singleElement()
                .satisfies(response -> assertThat(response.type()).isEqualTo("JOB_ACCEPTED"));
    }

    @Test
    void shouldReturnBadRequestForBlankRecipientLookup() {
        assertThatThrownBy(() -> service.listForRecipient("  ", user("worker@lanka.lk")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void shouldForbidLookupForAnotherRecipient() {
        assertThatThrownBy(() -> service.listForRecipient("other@lanka.lk", user("worker@lanka.lk")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void shouldAllowAdministratorToLookUpAnyRecipient() {
        when(notifications.findTop100ByRecipientOrderByIdDesc("worker@lanka.lk"))
                .thenReturn(List.of(notification(1L, "worker@lanka.lk", "JOB_ACCEPTED")));

        var result = service.listForRecipient("worker@lanka.lk",
                new AuthPrincipal(99L, "admin@lanka.lk", "Admin", "ADMIN"));

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldCalculateNotificationStatistics() {
        when(notifications.findDistinctTypes()).thenReturn(List.of("JOB_ACCEPTED", "USER_APPROVED"));
        when(notifications.count()).thenReturn(5L);
        when(notifications.countByStatus("SIMULATED")).thenReturn(3L);
        when(notifications.countByStatus("SENT")).thenReturn(1L);
        when(notifications.countByStatus("FAILED")).thenReturn(1L);
        when(notifications.countByType("JOB_ACCEPTED")).thenReturn(2L);
        when(notifications.countByType("USER_APPROVED")).thenReturn(3L);

        var result = service.stats();

        assertThat(result.total()).isEqualTo(5L);
        assertThat(result.simulated()).isEqualTo(3L);
        assertThat(result.sent()).isEqualTo(1L);
        assertThat(result.failed()).isEqualTo(1L);
        assertThat(result.byType()).containsEntry("JOB_ACCEPTED", 2L)
                .containsEntry("USER_APPROVED", 3L);
    }

    @Test
    void shouldDescribeConfiguredProviders() {
        when(providers.describe()).thenReturn(Map.of(
                "EMAIL", "SimulatedEmailProvider",
                "SMS", "SimulatedSmsProvider"));

        assertThat(service.providers()).containsEntry("EMAIL", "SimulatedEmailProvider")
                .containsEntry("SMS", "SimulatedSmsProvider");
        verify(providers).describe();
    }

    private AuthPrincipal user(String identifier) {
        return new AuthPrincipal(1L, identifier, "Test Worker", "WORKER");
    }

    private Notification notification(Long id, String recipient, String type) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setRecipient(recipient);
        notification.setChannel("EMAIL");
        notification.setType(type);
        notification.setMessage("Test message");
        notification.setStatus("SIMULATED");
        notification.setProvider("SimulatedEmailProvider");
        return notification;
    }
}
