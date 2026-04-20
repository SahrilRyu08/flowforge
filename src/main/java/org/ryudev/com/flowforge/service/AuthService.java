package org.ryudev.com.flowforge.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ryudev.com.flowforge.config.JwtTokenProvider;
import org.ryudev.com.flowforge.domain.Role;
import org.ryudev.com.flowforge.domain.User;
import org.ryudev.com.flowforge.domain.UserStatus;
import org.ryudev.com.flowforge.dto.request.LoginRequest;
import org.ryudev.com.flowforge.dto.request.RefreshTokenRequest;
import org.ryudev.com.flowforge.dto.request.RegisterRequest;
import org.ryudev.com.flowforge.dto.response.AuthResponse;
import org.ryudev.com.flowforge.dto.response.UserResponse;
import org.ryudev.com.flowforge.repository.TenantRepository;
import org.ryudev.com.flowforge.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponse login(@Valid LoginRequest request) {
        var tenant = tenantRepository.findBySlug(request.tenantSlug.trim().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Unknown tenant"));
        var user = userRepository.findByEmailAndTenant_Id(request.email.toLowerCase().trim(), tenant.getId())
                .orElseThrow(() -> new BadCredentialsException("Email atau password salah"));

        if (!passwordEncoder.matches(request.password, user.getPasswordHash())) {
            throw new BadCredentialsException("Email atau password salah");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new SecurityException("Akun tidak aktif atau telah dinonaktifkan");
        }

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshToken(@Valid RefreshTokenRequest request) {
        if (!jwtTokenProvider.validateToken(request.refreshToken())) {
            throw new SecurityException("Refresh token tidak valid atau kadaluarsa");
        }

        UUID userId = jwtTokenProvider.extractUserId(request.refreshToken());
        User user = userRepository.findByIdWithTenant(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Pengguna tidak ditemukan"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new SecurityException("Akun tidak aktif");
        }

        // Best practice: refresh token rotation (generate baru, invalidate lama)
        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        // Stateless JWT: logout biasanya dilakukan client-side dengan menghapus token.
        // Untuk true logout, simpan token ke blacklist/revocation table (Redis/DB)
        // jwtTokenProvider.revokeToken(refreshToken);
    }

    public UserResponse getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new SecurityException("Tidak terautentikasi");
        }

        UUID userId = UUID.fromString(auth.getName());
        User user = userRepository.findByIdWithTenant(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Data pengguna tidak ditemukan"));

        return UserResponse.from(user);
    }

    @Transactional
    public AuthResponse register(@Valid RegisterRequest request) {
        var tenant = tenantRepository.findBySlug(request.tenantSlug.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Tenant tidak valid"));

        if (userRepository.existsByEmailAndTenant_Id(request.email.toLowerCase().trim(), tenant.getId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Email sudah terdaftar pada tenant ini");
        }

        User user = User.builder()
                .tenant(tenant)
                .email(request.email.toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.password))
                .fullName(request.fullName)
                .role(request.role != null ? request.role : Role.VIEWER)
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(user);
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        long expiresIn = jwtTokenProvider.getExpirationInSeconds(accessToken);

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                expiresIn,
                UserResponse.from(user)
        );
    }
}
