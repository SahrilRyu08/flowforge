package org.ryudev.com.flowforge.repository;


import org.ryudev.com.flowforge.domain.User;
import org.ryudev.com.flowforge.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u FROM User u JOIN FETCH u.tenant WHERE u.id = :id")
    Optional<User> findByIdWithTenant(@Param("id") UUID id);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndTenant_Id(String email, UUID tenantId);

    boolean existsByEmailAndTenant_Id(String email, UUID tenantId);

    Page<User> findAllByTenant_Id(UUID tenantId, Pageable pageable);

    List<User> findAllByTenant_IdAndStatus(UUID tenantId, UserStatus status);

    @Query("SELECT COUNT(u) FROM User u WHERE u.tenant.id = :tenantId AND u.status = 'ACTIVE'")
    Long countActiveByTenantId(@Param("tenantId") UUID tenantId);
}
