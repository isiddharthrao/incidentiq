package com.incidentmgmt.config;

import com.incidentmgmt.entity.Role;
import com.incidentmgmt.entity.User;
import com.incidentmgmt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        User admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .role(Role.ADMIN)
                .fullName("System Administrator")
                .email("admin@incidentiq.local")
                .enabled(true)
                .build();
        userRepository.save(admin);
        log.info("Seeded default admin account: username=admin, password=admin123 — change immediately.");
    }
}
