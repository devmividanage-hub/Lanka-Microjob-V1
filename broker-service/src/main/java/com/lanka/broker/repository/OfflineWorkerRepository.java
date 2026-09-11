package com.lanka.broker.repository;

import com.lanka.broker.model.OfflineWorker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OfflineWorkerRepository extends JpaRepository<OfflineWorker, Long> {

    List<OfflineWorker> findAllByOrderByIdDesc();

    List<OfflineWorker> findByBrokerEntityIdOrderByIdDesc(Long brokerEntityId);

    long countByBrokerEntityId(Long brokerEntityId);

    long countByBrokerEntityIdAndStatus(Long brokerEntityId, String status);

    @Query("select coalesce(sum(w.commissionEarned), 0) from OfflineWorker w where w.brokerEntityId = :brokerEntityId")
    long sumCommissionByBroker(@Param("brokerEntityId") Long brokerEntityId);

    @Query("select coalesce(sum(w.totalJobs), 0) from OfflineWorker w where w.brokerEntityId = :brokerEntityId")
    long sumPlacementsByBroker(@Param("brokerEntityId") Long brokerEntityId);
}
