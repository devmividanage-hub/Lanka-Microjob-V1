package com.lanka.job.repository;

import com.lanka.job.model.BrokerPlacement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrokerPlacementRepository extends JpaRepository<BrokerPlacement, Long> {
    boolean existsByOfflineWorkerIdAndJobId(Long workerId, Long jobId);
    List<BrokerPlacement> findAllByOrderByIdDesc();
    List<BrokerPlacement> findByBrokerEntityIdOrderByIdDesc(Long brokerEntityId);
    List<BrokerPlacement> findByEmployerIdOrderByIdDesc(Long employerId);
}
