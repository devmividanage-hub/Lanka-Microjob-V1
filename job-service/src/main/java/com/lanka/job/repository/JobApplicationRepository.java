package com.lanka.job.repository;

import com.lanka.job.model.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    boolean existsByJobIdAndWorkerId(Long jobId, Long workerId);

    Optional<JobApplication> findByJobIdAndWorkerId(Long jobId, Long workerId);

    List<JobApplication> findByWorkerIdOrderByIdDesc(Long workerId);

    List<JobApplication> findByEmployerIdOrderByIdDesc(Long employerId);

    List<JobApplication> findByJobIdOrderByIdDesc(Long jobId);

    long countByWorkerId(Long workerId);

    long countByWorkerIdAndStatus(Long workerId, String status);

    long countByEmployerId(Long employerId);

    long countByEmployerIdAndStatus(Long employerId, String status);

    long countByJobId(Long jobId);

    long countByJobIdAndStatus(Long jobId, String status);

    long countByStatus(String status);

    /**
     * Cancels every still-pending application when the employer cancels (or the system expires)
     * the job, so workers are never left with an application on a job that can no longer happen.
     */
    @Modifying(clearAutomatically = true)
    @Query("update JobApplication a set a.status = 'CANCELLED', a.updatedAt = :now "
            + "where a.job.id = :jobId and a.status = 'APPLIED'")
    int cancelPendingForJob(@Param("jobId") Long jobId, @Param("now") LocalDateTime now);
}
