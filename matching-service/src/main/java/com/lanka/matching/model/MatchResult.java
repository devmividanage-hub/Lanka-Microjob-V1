package com.lanka.matching.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "match_results", indexes = {
        @Index(name = "idx_match_result_job", columnList = "job_id"),
        @Index(name = "idx_match_result_matched_at", columnList = "matched_at")
})
public class MatchResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long jobId;
    private String district;
    private int score;
    private String recommendation;
    @Column(length = 400)
    private String workerSkills;
    private LocalDateTime matchedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public String getWorkerSkills() {
        return workerSkills;
    }

    public void setWorkerSkills(String workerSkills) {
        this.workerSkills = workerSkills;
    }

    public LocalDateTime getMatchedAt() {
        return matchedAt;
    }

    public void setMatchedAt(LocalDateTime matchedAt) {
        this.matchedAt = matchedAt;
    }
}
