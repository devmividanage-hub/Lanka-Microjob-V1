package com.lanka.user.repository;

import com.lanka.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByMobile(String mobile);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByMobile(String mobile);

    List<User> findByStatusOrderByIdDesc(String status);

    List<User> findByRoleOrderByIdDesc(String role);

    List<User> findByRoleAndStatusOrderByIdDesc(String role, String status);

    long countByStatus(String status);

    long countByRoleAndStatus(String role, String status);
}
