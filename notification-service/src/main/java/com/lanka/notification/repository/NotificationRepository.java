package com.lanka.notification.repository;

import com.lanka.notification.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipient(String recipient);

    List<Notification> findTop100ByRecipientOrderByIdDesc(String recipient);

    List<Notification> findAllByOrderByIdDesc();

    long countByStatus(String status);

    long countByType(String type);

    @Query("select distinct n.type from Notification n order by n.type")
    List<String> findDistinctTypes();
}
