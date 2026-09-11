package com.lanka.broker.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanka.broker.dto.EligibleJobResponse;
import com.lanka.broker.dto.PlacementRecordResponse;
import com.lanka.broker.model.Broker;
import com.lanka.broker.model.OfflineWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

/** Required service-to-service client for real job eligibility and atomic slot reservations. */
@Component
public class JobServiceClient {
    private final RestClient restClient;
    private final String internalToken;
    private final ObjectMapper objectMapper;

    public JobServiceClient(@Value("${job.service.url:http://job-service:9002}") String baseUrl,
                            @Value("${notification.internal.token:}") String internalToken,
                            ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(8).toMillis());
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.internalToken = internalToken;
        this.objectMapper = objectMapper;
    }

    public List<EligibleJobResponse> eligible(Broker broker, OfflineWorker worker) {
        try {
            List<EligibleJobResponse> result = restClient.post().uri("/internal/jobs/eligible")
                    .header("X-Internal-Token", internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(workerPayload(broker, worker))
                    .retrieve().body(new ParameterizedTypeReference<>() {});
            return result == null ? List.of() : result;
        } catch (RestClientResponseException ex) {
            throw downstream(ex);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Job service is unavailable; available jobs could not be loaded");
        }
    }

    public PlacementRecordResponse place(Broker broker, OfflineWorker worker, Long jobId) {
        try {
            PlacementRecordResponse result = restClient.post().uri("/internal/jobs/{jobId}/broker-placements", jobId)
                    .header("X-Internal-Token", internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(workerPayload(broker, worker))
                    .retrieve().body(PlacementRecordResponse.class);
            if (result == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Job service returned no placement record");
            }
            return result;
        } catch (RestClientResponseException ex) {
            throw downstream(ex);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Job service is unavailable; no placement was created");
        }
    }

    public List<PlacementRecordResponse> placements(Long brokerEntityId) {
        try {
            String uri = brokerEntityId == null
                    ? "/internal/jobs/broker-placements"
                    : "/internal/jobs/broker-placements?brokerEntityId=" + brokerEntityId;
            List<PlacementRecordResponse> result = restClient.get().uri(uri)
                    .header("X-Internal-Token", internalToken)
                    .retrieve().body(new ParameterizedTypeReference<>() {});
            return result == null ? List.of() : result;
        } catch (RestClientResponseException ex) {
            throw downstream(ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Job service is unavailable; placement history could not be loaded");
        }
    }

    private WorkerPayload workerPayload(Broker broker, OfflineWorker worker) {
        return new WorkerPayload(broker.getId(), broker.getBrokerId(), worker.getId(), worker.getWorkerName(),
                worker.getDistrict(), worker.getCity(), worker.getSkills());
    }

    private ResponseStatusException downstream(RestClientResponseException ex) {
        String message = "Job service rejected the placement";
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            if (body.hasNonNull("message")) message = body.get("message").asText(message);
        } catch (Exception ignored) {
            // Keep the safe generic message when a downstream response is not JSON.
        }
        return new ResponseStatusException(ex.getStatusCode(), message);
    }

    private record WorkerPayload(Long brokerEntityId, String brokerId, Long workerId, String workerName,
                                 String district, String city, String skills) {
    }
}
