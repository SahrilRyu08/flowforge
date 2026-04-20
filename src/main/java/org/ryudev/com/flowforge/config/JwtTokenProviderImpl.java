package org.ryudev.com.flowforge.config;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.ryudev.com.flowforge.domain.User;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenProviderImpl implements JwtTokenProvider {

    private final JwtService jwtService;

    @Override
    public String generateAccessToken(User user) {
        FlowForgePrincipal principal = FlowForgePrincipal.from(user);
        Map<String, Object> claims = Map.of(
                "userId", user.getId().toString(),
                "tenantId", user.getTenant().getId().toString(),
                "role", user.getRole().name(),
                "fullName", user.getFullName()
        );
        return jwtService.generateToken(principal, claims);
    }

    @Override
    public String generateRefreshToken(User user) {
        FlowForgePrincipal principal = FlowForgePrincipal.from(user);
        return jwtService.generateRefreshToken(principal);
    }

    @Override
    public boolean validateToken(String token) {
        try {
            jwtService.extractAllClaims(token);
            return !jwtService.isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public UUID extractUserId(String token) {
        Claims claims = jwtService.extractAllClaims(token);
        String uid = claims.get("userId", String.class);
        if (uid != null) {
            return UUID.fromString(uid);
        }
        return UUID.fromString(claims.getSubject());
    }

    @Override
    public long getExpirationInSeconds(String token) {
        Claims claims = jwtService.extractAllClaims(token);
        return claims.getExpiration().toInstant().getEpochSecond() - claims.getIssuedAt().toInstant().getEpochSecond();
    }

    // Utility methods untuk ekstraksi claim tambahan
    public UUID extractTenantId(String token) {
        Claims claims = jwtService.extractAllClaims(token);
        return UUID.fromString(claims.get("tenantId", String.class));
    }

    public String extractRole(String token) {
        Claims claims = jwtService.extractAllClaims(token);
        return claims.get("role", String.class);
    }
}