package dev.vaultauth;

import dev.vaultauth.model.SecondFactorType;
import dev.vaultauth.model.User;
import dev.vaultauth.model.UserRole;
import dev.vaultauth.service.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@Slf4j
public class VaultAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(VaultAuthApplication.class, args);
    }

    /**
     * Seed some demo users on first start so the project works out of the box.
     * admin / admin123  → ADMIN role, no 2FA (so you can log straight in and enable it)
     * alice / alice123  → CLINICIAN role, 2FA disabled by default
     * viewer / viewer123 → VIEWER role
     */
    @Bean
    CommandLineRunner seedData(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.count() == 0) {
                userRepository.save(User.builder()
                        .username("admin")
                        .passwordHash(passwordEncoder.encode("admin123"))
                        .email("admin@vaultauth.dev")
                        .displayName("System Admin")
                        .role(UserRole.ADMIN)
                        .twoFactorEnabled(false)
                        .forcePasswordChange(false)
                        .accountLocked(false)
                        .failedLoginAttempts(0)
                        .build());

                userRepository.save(User.builder()
                        .username("alice")
                        .passwordHash(passwordEncoder.encode("alice123"))
                        .email("alice@vaultauth.dev")
                        .displayName("Alice Nguyen")
                        .role(UserRole.CLINICIAN)
                        .twoFactorEnabled(false)
                        .forcePasswordChange(false)
                        .accountLocked(false)
                        .failedLoginAttempts(0)
                        .build());

                userRepository.save(User.builder()
                        .username("viewer")
                        .passwordHash(passwordEncoder.encode("viewer123"))
                        .email("viewer@vaultauth.dev")
                        .displayName("Read-Only Viewer")
                        .role(UserRole.VIEWER)
                        .twoFactorEnabled(false)
                        .forcePasswordChange(true)
                        .accountLocked(false)
                        .failedLoginAttempts(0)
                        .build());

                log.info("Demo users seeded: admin / alice / viewer");
            }
        };
    }
}
