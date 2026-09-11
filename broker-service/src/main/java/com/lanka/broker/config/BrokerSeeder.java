package com.lanka.broker.config;

import com.lanka.broker.model.Broker;
import com.lanka.broker.model.OfflineWorker;
import com.lanka.broker.repository.BrokerRepository;
import com.lanka.broker.repository.OfflineWorkerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Seeds one approved demo broker (reference BRK-0001) plus two offline workers on an empty
 * database, so the broker dashboard can be demonstrated straight away.
 *
 * <p>Credentials come from {@code SEED_DEMO_BROKER_EMAIL} / {@code SEED_DEMO_BROKER_PASSWORD}; the
 * defaults are documented development values. Set {@code SEED_DEMO_DATA=false} in production.</p>
 */
@Component
public class BrokerSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(BrokerSeeder.class);

    private final BrokerRepository brokers;
    private final OfflineWorkerRepository workers;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String email;
    private final String password;

    public BrokerSeeder(BrokerRepository brokers, OfflineWorkerRepository workers, PasswordEncoder passwordEncoder,
                        @Value("${app.seed.demo-data:true}") boolean enabled,
                        @Value("${app.seed.demo-broker-email}") String email,
                        @Value("${app.seed.demo-broker-password}") String password) {
        this.brokers = brokers;
        this.workers = workers;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.info("Demo broker seeding disabled (app.seed.demo-data=false)");
            return;
        }
        if (brokers.count() > 0) {
            return;
        }
        Broker broker = new Broker();
        broker.setName("Demo Broker");
        broker.setNic("823456789V");
        broker.setPhone("0771000001");
        broker.setEmail(email);
        broker.setPassword(passwordEncoder.encode(password));
        broker.setBrokerId("BRK-0001");
        broker.setDistrict("Gampaha");
        broker.setCity("Negombo");
        broker.setStatus("APPROVED");
        broker.setYearsExperience("3-5 years");
        broker.setEstimatedWorkers("6-15 workers");
        broker.setWorkerMethod("Demo broker seeded for evaluation purposes");
        broker.setSubmittedAt(LocalDateTime.now());
        broker.setReviewedAt(LocalDateTime.now());
        Broker saved = brokers.save(broker);

        List<OfflineWorker> demoWorkers = List.of(
                offlineWorker(saved, "Demo Worker One", "912345678V", "0772000001", "Construction,Masonry", "Full Days"),
                offlineWorker(saved, "Demo Worker Two", "934567812V", "0772000002", "Agriculture", "Half Days"));
        workers.saveAll(demoWorkers);
        saved.setTotalWorkers(demoWorkers.size());
        brokers.save(saved);
        log.info("Seeded demo broker {} ({}) with {} offline worker(s). Password: SEED_DEMO_BROKER_PASSWORD",
                saved.getBrokerId(), saved.getEmail(), demoWorkers.size());
    }

    private OfflineWorker offlineWorker(Broker broker, String name, String nic, String mobile, String skills,
                                        String availability) {
        OfflineWorker worker = new OfflineWorker();
        worker.setBrokerId(broker.getBrokerId());
        worker.setBrokerEntityId(broker.getId());
        worker.setWorkerName(name);
        worker.setWorkerNic(nic);
        worker.setMobile(mobile);
        worker.setDistrict(broker.getDistrict());
        worker.setCity(broker.getCity());
        worker.setSkills(skills);
        worker.setAvailability(availability);
        worker.setStatus("ACTIVE");
        return worker;
    }
}
