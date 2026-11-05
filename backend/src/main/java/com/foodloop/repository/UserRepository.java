package com.foodloop.repository;

import com.foodloop.entity.*;
import com.foodloop.entity.enums.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<User> findByRole_NameAndStatus(String roleName, UserStatus status, Pageable pageable);

    long countByRole_Name(String roleName);

    @Query("select u from User u "
            + "where (:role = '' or u.role.name = :role) "
            + "and (:status is null or u.status = :status) "
            + "and (:q = '' or lower(u.email) like lower(concat('%', :q, '%')) "
            + "     or lower(u.fullName) like lower(concat('%', :q, '%')))")
    Page<User> search(@Param("role") String role,
                      @Param("status") UserStatus status,
                      @Param("q") String q,
                      Pageable pageable);
}