package org.ryudev.com.flowforge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ryudev.com.flowforge.config.FlowForgePrincipal;
import org.ryudev.com.flowforge.domain.User;
import org.ryudev.com.flowforge.dto.response.UserResponse;
import org.ryudev.com.flowforge.exception.UnAuthorizedException;
import org.ryudev.com.flowforge.repository.TenantRepository;
import org.ryudev.com.flowforge.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.beans.Transient;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Page<UserResponse> listUser(Pageable pageable) {
        UUID tenantId = getCurrentTenantId();
        return userRepository.findAllByTenant_Id(tenantId,pageable).map(this::toResponse);
    }

    private UserResponse toResponse(User u) {
        return UserResponse.builder()
                .id(u.getId())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .role(u.getRole())
                .status(u.getStatus())
                .tenant(u.getTenant().getId())
                .tenantSlug(u.getTenant().getSlug())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }

    private UUID getCurrentTenantId() {
        return getCurrentPrincipal().tenantId();
    }

    private FlowForgePrincipal getCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof FlowForgePrincipal p) {
            return p;
        } else {
            throw new UnAuthorizedException("Not authenticated");
        }
    }

}
