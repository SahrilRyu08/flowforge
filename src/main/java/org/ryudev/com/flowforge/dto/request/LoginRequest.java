package org.ryudev.com.flowforge.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mutable JSON DTO — public fields for Bean Validation + test ergonomics. */
public class LoginRequest {
    @Email
    @NotBlank
    public String email;

    @NotBlank
    @Size(min = 8, max = 100)
    public String password;

    @NotBlank
    public String tenantSlug;
}
