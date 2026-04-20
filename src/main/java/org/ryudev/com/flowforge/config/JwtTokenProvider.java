package org.ryudev.com.flowforge.config;

import org.ryudev.com.flowforge.domain.User;
import java.util.UUID;

public interface JwtTokenProvider {
    String generateAccessToken(User user);
    String generateRefreshToken(User user);
    boolean validateToken(String token);
    UUID extractUserId(String token);
    long getExpirationInSeconds(String token);
}
