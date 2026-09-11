package com.lanka.broker.repository;

import com.lanka.broker.model.Broker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BrokerRepository extends JpaRepository<Broker, Long> {
    Optional<Broker> findByBrokerId(String brokerId);

    Optional<Broker> findByEmail(String email);

    Optional<Broker> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByBrokerId(String brokerId);

    List<Broker> findByStatus(String status);

    List<Broker> findByStatusOrderByIdDesc(String status);

    List<Broker> findAllByOrderByIdDesc();

    long countByStatus(String status);

    /** Approved brokers grouped by district with workers counted from offline_worker, not a cached column. */
    @Query(value = "select coalesce(b.district, 'Unassigned'), count(distinct b.id), count(w.id) "
            + "from broker b left join offline_worker w on w.broker_entity_id = b.id "
            + "where b.status = 'APPROVED' group by b.district order by count(distinct b.id) desc",
            nativeQuery = true)
    List<Object[]> approvedBrokersByDistrict();
}
