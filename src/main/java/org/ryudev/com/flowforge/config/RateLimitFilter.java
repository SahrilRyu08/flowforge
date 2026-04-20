package org.ryudev.com.flowforge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Token-bucket style limiter per client IP for {@code /api/**} using Redis INCR + TTL.
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter implements Ordered {

    @Override
    public int getOrder() {
        return 100;
    }

    private final StringRedisTemplate redis;

    @Value("${flowforge.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${flowforge.rate-limit.burst:200}")
    private int burst;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        String key = "rl:" + ip + ":" + (request.getRequestURI().hashCode() & 0x7fffffff);

        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(1));
        }

        if (count != null && count > burst) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static String clientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
