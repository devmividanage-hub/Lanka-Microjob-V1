package com.lanka.job.repository;

import com.lanka.job.model.Job;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Collection;

/**
 * Composable filters for the job search. Using Specifications keeps every combination of
 * district / city / category / free-text search supported by a single query instead of one
 * derived finder per combination.
 */
public final class JobSpecifications {

    private JobSpecifications() {
    }

    public static Specification<Job> statusIn(Collection<String> statuses) {
        return (root, query, cb) -> statuses == null || statuses.isEmpty()
                ? cb.conjunction()
                : root.get("status").in(statuses);
    }

    public static Specification<Job> employerId(Long employerId) {
        return (root, query, cb) -> employerId == null ? cb.conjunction() : cb.equal(root.get("employerId"), employerId);
    }

    public static Specification<Job> district(String district) {
        return (root, query, cb) -> isBlank(district)
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("district")), district.trim().toLowerCase());
    }

    public static Specification<Job> city(String city) {
        return (root, query, cb) -> isBlank(city)
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("city")), city.trim().toLowerCase());
    }

    public static Specification<Job> category(String category) {
        return (root, query, cb) -> isBlank(category)
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase());
    }

    /** Case-insensitive free-text match across the fields exposed by the job-feed search box. */
    public static Specification<Job> search(String term) {
        return (root, query, cb) -> {
            if (isBlank(term)) {
                return cb.conjunction();
            }
            String pattern = "%" + term.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("employer")), pattern),
                    cb.like(cb.lower(root.get("category")), pattern),
                    cb.like(cb.lower(root.get("requiredSkills")), pattern),
                    cb.like(cb.lower(root.get("district")), pattern),
                    cb.like(cb.lower(root.get("city")), pattern)
            );
        };
    }

    /** Keeps jobs whose working day has not passed yet (a null date is treated as open-ended). */
    public static Specification<Job> notExpired(LocalDate today) {
        return (root, query, cb) -> cb.or(cb.isNull(root.get("jobDate")),
                cb.greaterThanOrEqualTo(root.get("jobDate"), today));
    }

    /** Only jobs that still have at least one free slot. */
    public static Specification<Job> hasFreeSlots() {
        return (root, query, cb) -> cb.greaterThan(root.get("slotsRemaining"), 0);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
