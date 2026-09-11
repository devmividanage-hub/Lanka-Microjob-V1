package com.lanka.job.config;

import com.lanka.job.dto.JobRequest;
import com.lanka.job.repository.JobRepository;
import com.lanka.job.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Seeds a small demo catalogue the first time the service runs against an empty database, so the
 * worker feed is not blank during a demonstration.
 *
 * <p>Set {@code SEED_DEMO_DATA=false} for production. Seeded jobs are platform demo data: they have
 * no employer owner ({@code employerId = null}), which means only an administrator can accept or
 * reject applications on them. Real employer jobs are created through {@code POST /jobs} and are
 * owned by the authenticated employer.</p>
 */
@Component
public class JobSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(JobSeeder.class);

    private final JobRepository jobs;
    private final JobService service;
    private final boolean enabled;

    public JobSeeder(JobRepository jobs, JobService service,
                     @Value("${app.seed.demo-data:true}") boolean enabled) {
        this.jobs = jobs;
        this.service = service;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.info("Demo job seeding disabled (app.seed.demo-data=false)");
            return;
        }
        if (jobs.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now();
        service.persistNewJob(demo("3 Construction Workers", "Construction", "Colombo", "Colombo City", 3, 2500,
                today.plusDays(1), true, "Heavy Lifting,Masonry", "Urgent construction support"), null, "Silva Constructions", null);
        service.persistNewJob(demo("Harvest Helpers x5", "Agriculture", "Gampaha", "Negombo", 5, 1800,
                today.plusDays(2), false, "No Experience Needed", "Harvest support"), null, "Perera Farms", null);
        service.persistNewJob(demo("Delivery Driver", "Driving", "Colombo", "Dehiwala", 1, 3200,
                today.plusDays(1), true, "Driving License", "Own license required"), null, "FastCargo LK", null);
        service.persistNewJob(demo("Office Deep Cleaning x2", "Cleaning", "Galle", "Galle", 2, 2000,
                today.plusDays(3), false, "Cleaning", "Office cleaning"), null, "CleanPro", null);
        service.persistNewJob(demo("Painting Team x4", "Painting", "Kandy", "Kandy City", 4, 2800,
                today.plusDays(4), false, "Painting", "Interior painting"), null, "Rainbow Interiors", null);
        service.persistNewJob(demo("Event Setup x6", "Event Staff", "Colombo", "Kotte", 6, 2200,
                today.plusDays(1), true, "Heavy Lifting", "Event setup crew"), null, "Golden Events", null);
        log.info("Seeded {} demo jobs (SEED_DEMO_DATA=false disables this)", jobs.count());
    }

    private JobRequest demo(String title, String category, String district, String city, int workers, int pay,
                            LocalDate date, boolean urgent, String skills, String notes) {
        return new JobRequest(title, category, district, city, workers, pay, date, urgent, skills, notes);
    }
}
