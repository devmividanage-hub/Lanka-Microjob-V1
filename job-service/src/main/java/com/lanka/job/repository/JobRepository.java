package com.lanka.job.repository;

import com.lanka.job.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {

    /** Serialises competing application/placement slot updates for the same job. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from Job job where job.id = :id")
    Optional<Job> findByIdForUpdate(@Param("id") Long id);

    List<Job> findAllByOrderByIdDesc();

    List<Job> findByEmployerIdOrderByIdDesc(Long employerId);

    long countByEmployerId(Long employerId);

    long countByEmployerIdAndStatus(Long employerId, String status);

    long countByStatus(String status);

    /** Jobs that are still marked OPEN although their working day has already passed. */
    List<Job> findByStatusAndJobDateBefore(String status, LocalDate date);

    List<Job> findByStatusIn(List<String> statuses);
}
