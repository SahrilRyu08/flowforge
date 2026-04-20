package org.ryudev.com.flowforge.config;

import lombok.RequiredArgsConstructor;
import org.ryudev.com.flowforge.domain.User;
import org.ryudev.com.flowforge.domain.UserStatus;
import org.ryudev.com.flowforge.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class FlowForgeUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String subject) throws UsernameNotFoundException {
        UUID userId;
        try {
            userId = UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new UsernameNotFoundException("Invalid user id in token");
        }

        User user = userRepository.findByIdWithTenant(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UsernameNotFoundException("User account is not active");
        }

        return FlowForgePrincipal.from(user);
    }
}
