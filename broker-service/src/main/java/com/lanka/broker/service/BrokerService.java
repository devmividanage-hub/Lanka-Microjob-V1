package com.lanka.broker.service;

import com.lanka.broker.client.NotificationClient;
import com.lanka.broker.client.JobServiceClient;
import com.lanka.broker.dto.*;
import com.lanka.broker.model.Broker;
import com.lanka.broker.model.OfflineWorker;
import com.lanka.broker.repository.BrokerRepository;
import com.lanka.broker.repository.OfflineWorkerRepository;
import com.lanka.broker.security.AuthPrincipal;
import com.lanka.broker.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Broker applications, admin review, offline worker management and commission.
 *
 * <p>Authorization model: applying and logging in are public; reviewing applications requires an
 * ADMIN token; everything under a specific {@code brokerId} requires a BROKER token whose
 * {@code uid} claim equals that broker's primary key (or an ADMIN token). The brokerId in the URL
 * is never trusted on its own.</p>
 */
@Service
public class BrokerService {
    private static final Logger log = LoggerFactory.getLogger(BrokerService.class);
    private static final List<String> WORKER_STATUSES = List.of("ACTIVE", "ON_JOB", "INACTIVE");

    private final BrokerRepository brokers;
    private final OfflineWorkerRepository workers;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwt;
    private final NotificationClient notifications;
    private final JobServiceClient jobService;
    private final double commissionRate;
    private final String adminContact;

    public BrokerService(BrokerRepository brokers, OfflineWorkerRepository workers, PasswordEncoder passwordEncoder,
                         JwtUtil jwt, NotificationClient notifications, JobServiceClient jobService,
                         @Value("${app.broker.commission-rate:0.075}") double commissionRate,
                         @Value("${app.admin.contact-email:admin@gmail.com}") String adminContact) {
        this.brokers = brokers;
        this.workers = workers;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.notifications = notifications;
        this.jobService = jobService;
        this.commissionRate = commissionRate;
        this.adminContact = adminContact;
    }

    // --- public application + login -------------------------------------------------------------

    @Transactional
    public BrokerResponse apply(BrokerRequest request) {
        String email = normalizeEmail(request.email());
        String phone = normalize(request.phone());
        if (email != null && brokers.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A broker application with this email already exists");
        }
        if (phone != null && brokers.existsByPhone(phone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A broker application with this mobile number already exists");
        }

        Broker broker = new Broker();
        broker.setName(normalize(request.name()));
        broker.setNic(normalize(request.nic()));
        broker.setPhone(phone);
        broker.setEmail(email);
        broker.setPassword(passwordEncoder.encode(request.password()));
        broker.setDistrict(normalize(request.district()));
        broker.setCity(normalize(request.city()));
        broker.setYearsExperience(normalize(request.yearsExperience()));
        broker.setEstimatedWorkers(normalize(request.estimatedWorkers()));
        broker.setWorkerMethod(normalize(request.workerMethod()));
        broker.setIdProof(normalize(request.idProof()));
        broker.setStatus("PENDING");
        Broker saved = brokers.save(broker);

        notifications.notifyAuto(contactOf(saved), "BROKER_APPLICATION_SUBMITTED",
                "Thank you " + saved.getName() + ". Your broker application for " + saved.getDistrict()
                        + " District was received and is waiting for admin review.");
        notifications.notifyAuto(adminContact, "BROKER_PENDING_APPROVAL",
                "New broker application: " + saved.getName() + " (" + saved.getDistrict() + " District)");
        log.info("Broker application received from {} ({})", saved.getName(), saved.getEmail());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public BrokerLoginResponse login(BrokerLoginRequest request) {
        String email = normalizeEmail(request.email());
        String phone = normalize(request.phone());
        Broker broker = email != null ? brokers.findByEmail(email).orElse(null) : brokers.findByPhone(phone).orElse(null);
        boolean passwordMatches = broker != null && broker.getPassword() != null
                && passwordEncoder.matches(request.password(), broker.getPassword());
        if (broker == null || !passwordMatches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid broker login credentials");
        }
        if (!"APPROVED".equalsIgnoreCase(broker.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Broker account is not approved yet (current status: " + broker.getStatus() + ")");
        }
        String subject = broker.getEmail() != null ? broker.getEmail() : broker.getPhone();
        String token = jwt.generateToken(subject, "BROKER", broker.getId(), broker.getName(),
                Map.of("brokerId", broker.getBrokerId() == null ? "" : broker.getBrokerId(),
                        "district", broker.getDistrict() == null ? "" : broker.getDistrict()));
        return new BrokerLoginResponse(token, broker.getId(), broker.getBrokerId(), broker.getName(),
                broker.getEmail(), broker.getPhone(), broker.getDistrict(), broker.getCity(), broker.getStatus());
    }

    // --- admin review ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<BrokerResponse> list() {
        return brokers.findAllByOrderByIdDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BrokerResponse> getPending() {
        return brokers.findByStatusOrderByIdDesc("PENDING").stream().map(this::toResponse).toList();
    }

    @Transactional
    public BrokerResponse approve(Long id) {
        Broker broker = findBroker(id);
        if ("APPROVED".equals(broker.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Broker is already approved");
        }
        broker.setStatus("APPROVED");
        if (broker.getBrokerId() == null || broker.getBrokerId().isBlank()) {
            broker.setBrokerId(nextBrokerId());
        }
        broker.setReviewedAt(LocalDateTime.now());
        Broker saved = brokers.save(broker);
        notifications.notifyAuto(contactOf(saved), "BROKER_APPROVED",
                "Your broker application was approved. Your Broker ID is " + saved.getBrokerId()
                        + ". You can now log in and register offline workers in " + saved.getDistrict() + " District.");
        log.info("Broker {} approved with reference {}", id, saved.getBrokerId());
        return toResponse(saved);
    }

    @Transactional
    public BrokerResponse reject(Long id) {
        Broker broker = findBroker(id);
        if ("REJECTED".equals(broker.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Broker is already rejected");
        }
        broker.setStatus("REJECTED");
        broker.setReviewedAt(LocalDateTime.now());
        Broker saved = brokers.save(broker);
        notifications.notifyAuto(contactOf(saved), "BROKER_REJECTED",
                "Your Lanka MicroJob broker application was not approved. Please contact support for details.");
        log.info("Broker {} rejected", id);
        return toResponse(saved);
    }

    /** Real broker counters for the admin dashboard, including the per-district breakdown. */
    @Transactional(readOnly = true)
    public BrokerStatsResponse stats() {
        List<Map<String, Object>> byDistrict = new ArrayList<>();
        for (Object[] row : brokers.approvedBrokersByDistrict()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("district", row[0] == null ? "Unassigned" : row[0].toString());
            entry.put("brokers", ((Number) row[1]).longValue());
            entry.put("workers", ((Number) row[2]).longValue());
            byDistrict.add(entry);
        }
        return new BrokerStatsResponse(
                brokers.count(),
                brokers.countByStatus("PENDING"),
                brokers.countByStatus("APPROVED"),
                brokers.countByStatus("REJECTED"),
                workers.count(),
                workers.findAll().stream().filter(worker -> "ACTIVE".equalsIgnoreCase(worker.getStatus())).count(),
                byDistrict);
    }

    /** Non-sensitive counters used by the public landing page. */
    @Transactional(readOnly = true)
    public Map<String, Long> publicStats() {
        return Map.of(
                "approvedBrokers", brokers.countByStatus("APPROVED"),
                "totalOfflineWorkers", workers.count());
    }

    /** Complete offline-worker directory for the admin detail view. */
    @Transactional(readOnly = true)
    public List<OfflineWorkerResponse> listAllWorkers() {
        Map<Long, Double> ratings = placementRatings(jobService.placements(null));
        return workers.findAllByOrderByIdDesc().stream()
                .map(worker -> toWorkerResponse(worker, ratings.get(worker.getId()))).toList();
    }

    // --- broker's own resources -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public BrokerDashboardResponse getDashboard(String brokerId, AuthPrincipal principal) {
        Broker broker = requireBrokerAccess(brokerId, principal);
        List<OfflineWorker> owned = workers.findByBrokerEntityIdOrderByIdDesc(broker.getId());
        Map<Long, Double> ratings = placementRatings(jobService.placements(broker.getId()));
        long active = owned.stream().filter(worker -> "ACTIVE".equalsIgnoreCase(worker.getStatus())).count();
        long onJob = owned.stream().filter(worker -> "ON_JOB".equalsIgnoreCase(worker.getStatus())).count();
        long inactive = owned.stream().filter(worker -> "INACTIVE".equalsIgnoreCase(worker.getStatus())).count();
        // Null (not 0.0) while no offline worker has been rated, so the UI shows "no ratings yet".
        java.util.OptionalDouble average = owned.stream()
                .map(worker -> ratings.getOrDefault(worker.getId(), worker.getRating()))
                .filter(rating -> rating != null && rating > 0)
                .mapToDouble(Double::doubleValue)
                .average();
        Double averageRating = average.isPresent() ? Math.round(average.getAsDouble() * 10) / 10.0 : null;
        return new BrokerDashboardResponse(broker.getBrokerId(), broker.getId(), broker.getName(), broker.getEmail(),
                broker.getPhone(), broker.getDistrict(), broker.getCity(), broker.getStatus(), owned.size(), active,
                onJob, inactive, workers.sumPlacementsByBroker(broker.getId()),
                workers.sumCommissionByBroker(broker.getId()), averageRating, commissionRate,
                owned.stream().map(worker -> toWorkerResponse(worker, ratings.get(worker.getId()))).toList());
    }

    @Transactional(readOnly = true)
    public List<OfflineWorkerResponse> getWorkers(String brokerId, AuthPrincipal principal) {
        Broker broker = requireBrokerAccess(brokerId, principal);
        Map<Long, Double> ratings = placementRatings(jobService.placements(broker.getId()));
        return workers.findByBrokerEntityIdOrderByIdDesc(broker.getId()).stream()
                .map(worker -> toWorkerResponse(worker, ratings.get(worker.getId()))).toList();
    }

    @Transactional
    public OfflineWorkerResponse addOfflineWorker(String brokerId, OfflineWorkerRequest request, AuthPrincipal principal) {
        Broker broker = requireBrokerAccess(brokerId, principal);
        if (!"APPROVED".equalsIgnoreCase(broker.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an approved broker can register workers");
        }

        OfflineWorker worker = new OfflineWorker();
        worker.setBrokerId(broker.getBrokerId());
        worker.setBrokerEntityId(broker.getId());
        worker.setWorkerName(normalize(request.workerName()));
        worker.setWorkerNic(normalize(request.workerNic()));
        worker.setMobile(normalize(request.mobile()));
        // City lock: district and city always come from the broker's own approved assignment.
        worker.setDistrict(broker.getDistrict());
        worker.setCity(broker.getCity());
        worker.setSkills(normalizeSkills(request.skills()));
        worker.setAvailability(normalize(request.availability()));
        worker.setStatus("ACTIVE");
        OfflineWorker saved = workers.save(worker);

        broker.setTotalWorkers((int) workers.countByBrokerEntityId(broker.getId()));
        brokers.save(broker);

        notifications.notifyAuto(contactOf(broker), "OFFLINE_WORKER_REGISTERED",
                saved.getWorkerName() + " was registered under " + broker.getBrokerId() + " ("
                        + broker.getDistrict() + " District).");
        log.info("Offline worker {} registered by broker {}", saved.getId(), broker.getBrokerId());
        return toWorkerResponse(saved);
    }

    @Transactional
    public OfflineWorkerResponse updateWorkerStatus(String brokerId, Long workerId, String status,
                                                    AuthPrincipal principal) {
        Broker broker = requireBrokerAccess(brokerId, principal);
        OfflineWorker worker = findOwnedWorker(broker, workerId);
        String target = status == null ? "" : status.trim().toUpperCase();
        if (!WORKER_STATUSES.contains(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported worker status. Allowed: " + String.join(", ", WORKER_STATUSES));
        }
        worker.setStatus(target);
        return toWorkerResponse(workers.save(worker));
    }

    /** Fresh, server-filtered real jobs for the placement picker. */
    @Transactional(readOnly = true)
    public List<EligibleJobResponse> eligibleJobs(String brokerId, Long workerId, AuthPrincipal principal) {
        Broker broker = requireBrokerAccess(brokerId, principal);
        OfflineWorker worker = findOwnedWorker(broker, workerId);
        requirePlaceable(broker, worker);
        return jobService.eligible(broker, worker);
    }

    /** Full placement history for one broker, or the platform-wide list for an admin. */
    @Transactional(readOnly = true)
    public List<PlacementRecordResponse> placements(String brokerId, AuthPrincipal principal) {
        Broker broker = requireBrokerAccess(brokerId, principal);
        return jobService.placements(broker.getId());
    }

    @Transactional(readOnly = true)
    public List<PlacementRecordResponse> allPlacements() {
        return jobService.placements(null);
    }

    /**
     * Places an owned worker into one real job. Job-service locks the job row, re-validates
     * eligibility, derives pay/commission and consumes the slot before these dashboard totals move.
     */
    @Transactional
    public PlacementResponse recordPlacement(String brokerId, Long workerId, PlacementRequest request,
                                             AuthPrincipal principal) {
        Broker broker = requireBrokerAccess(brokerId, principal);
        OfflineWorker worker = findOwnedWorker(broker, workerId);
        requirePlaceable(broker, worker);

        PlacementRecordResponse placement = jobService.place(broker, worker, request.jobId());
        long commission = placement.commissionAmount();

        worker.setTotalJobs((worker.getTotalJobs() == null ? 0 : worker.getTotalJobs()) + 1);
        worker.setCommissionEarned((worker.getCommissionEarned() == null ? 0L : worker.getCommissionEarned()) + commission);
        worker.setStatus("ON_JOB");
        workers.save(worker);

        broker.setTotalPlacements((broker.getTotalPlacements() == null ? 0L : broker.getTotalPlacements()) + 1);
        broker.setCommissionEarned((broker.getCommissionEarned() == null ? 0L : broker.getCommissionEarned()) + commission);
        brokers.save(broker);

        notifications.notifyAuto(contactOf(broker), "PLACEMENT_RECORDED",
                worker.getWorkerName() + " placed on " + placement.jobTitle()
                        + ". Commission earned: Rs." + commission + ".");
        log.info("Placement {} recorded for offline worker {} on job {} by broker {} (commission {})",
                placement.placementId(), worker.getId(), placement.jobId(), broker.getBrokerId(), commission);
        return new PlacementResponse(placement, toWorkerResponse(worker), commission,
                broker.getCommissionEarned(), broker.getTotalPlacements());
    }

    // --- helpers --------------------------------------------------------------------------------

    /** Loads the broker behind {@code brokerId} and proves the caller may act on it. */
    @Transactional(readOnly = true)
    public Broker requireBrokerAccess(String brokerId, AuthPrincipal principal) {
        if (principal == null || principal.uid() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Broker broker = brokers.findByBrokerId(brokerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Broker not found"));
        if (principal.isAdmin()) {
            return broker;
        }
        if (!principal.hasRole("BROKER") || !principal.owns(broker.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your own broker account");
        }
        return broker;
    }

    private OfflineWorker findOwnedWorker(Broker broker, Long workerId) {
        OfflineWorker worker = workers.findById(workerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offline worker not found"));
        if (!broker.getId().equals(worker.getBrokerEntityId())) {
            // IDOR guard: the worker exists but belongs to another broker.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This worker is managed by another broker");
        }
        return worker;
    }

    private void requirePlaceable(Broker broker, OfflineWorker worker) {
        if (!"APPROVED".equalsIgnoreCase(broker.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an approved broker can place workers");
        }
        if (!"ACTIVE".equalsIgnoreCase(worker.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only an ACTIVE worker can be placed (current status: " + worker.getStatus() + ")");
        }
    }

    private Broker findBroker(Long id) {
        return brokers.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Broker not found"));
    }

    /** Collision-free broker reference: keeps probing until an unused value is found. */
    private String nextBrokerId() {
        long candidate = brokers.count() + 1;
        String brokerId = String.format("BRK-%04d", candidate);
        while (brokers.existsByBrokerId(brokerId)) {
            candidate++;
            brokerId = String.format("BRK-%04d", candidate);
        }
        return brokerId;
    }

    private String contactOf(Broker broker) {
        return broker.getEmail() != null && !broker.getEmail().isBlank() ? broker.getEmail() : broker.getPhone();
    }

    private BrokerResponse toResponse(Broker broker) {
        int actualWorkerCount = broker.getId() == null
                ? 0
                : Math.toIntExact(workers.countByBrokerEntityId(broker.getId()));
        return new BrokerResponse(broker.getId(), broker.getBrokerId(), broker.getName(), broker.getNic(),
                broker.getEmail(), broker.getPhone(), broker.getDistrict(), broker.getCity(), broker.getStatus(),
                broker.getYearsExperience(), broker.getEstimatedWorkers(), broker.getWorkerMethod(),
                broker.getIdProof(), actualWorkerCount, broker.getCommissionEarned(),
                broker.getTotalPlacements(), broker.getSubmittedAt(), broker.getReviewedAt());
    }

    private OfflineWorkerResponse toWorkerResponse(OfflineWorker worker) {
        return toWorkerResponse(worker, null);
    }

    private OfflineWorkerResponse toWorkerResponse(OfflineWorker worker, Double placementRating) {
        return new OfflineWorkerResponse(worker.getId(), worker.getBrokerId(), worker.getWorkerName(),
                worker.getWorkerNic(), worker.getMobile(), worker.getDistrict(), worker.getCity(), worker.getSkills(),
                worker.getAvailability(), worker.getStatus(), worker.getTotalJobs(),
                placementRating == null ? worker.getRating() : placementRating,
                worker.getCommissionEarned(), worker.getRegisteredAt());
    }

    /** Average of real employer ratings, grouped by the offline-worker reference. */
    private Map<Long, Double> placementRatings(List<PlacementRecordResponse> placements) {
        return placements.stream().filter(placement -> placement.rating() != null)
                .collect(Collectors.groupingBy(PlacementRecordResponse::workerId,
                        Collectors.averagingInt(PlacementRecordResponse::rating)));
    }

    private String normalizeSkills(String value) {
        String text = normalize(value);
        if (text == null) return null;
        List<String> skills = java.util.Arrays.stream(text.split(","))
                .map(String::trim).filter(skill -> !skill.isEmpty()).distinct().toList();
        return skills.isEmpty() ? null : String.join(",", skills);
    }

    private String normalizeEmail(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
