package org.ryudev.com.flowforge.dto.response;

import lombok.Builder;
import lombok.Data;
import org.ryudev.com.flowforge.domain.Role;
import org.ryudev.com.flowforge.domain.User;
import org.ryudev.com.flowforge.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class UserResponse {
    private UUID id;
    private UUID tenant;
    private String tenantSlug;
    private String email;
    private String fullName;
    private Role role;
    private UserStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public static UserResponse from(User u) {
        return UserResponse.builder()
                .id(u.getId())
                .tenant(u.getTenant().getId())
                .tenantSlug(u.getTenant().getSlug())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .role(u.getRole())
                .status(u.getStatus())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }
}
