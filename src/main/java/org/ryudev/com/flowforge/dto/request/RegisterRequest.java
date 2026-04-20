package org.ryudev.com.flowforge.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.ryudev.com.flowforge.domain.Role;

public class RegisterRequest {
    @NotBlank
    @Size(max = 200)
    public String fullName;

    @Email
    @NotBlank
    public String email;

    @NotBlank
    @Size(min = 8, max = 100)
    public String password;

    @NotBlank
    public String tenantSlug;

    public Role role = Role.VIEWER;
}
