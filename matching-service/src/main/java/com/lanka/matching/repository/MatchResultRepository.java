package com.lanka.matching.repository;

import com.lanka.matching.model.MatchResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {
    List<MatchResult> findByJobId(Long jobId);

    List<MatchResult> findByJobIdOrderByIdDesc(Long jobId);
}
