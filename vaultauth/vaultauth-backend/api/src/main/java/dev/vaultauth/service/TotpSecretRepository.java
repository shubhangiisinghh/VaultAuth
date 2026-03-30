package dev.vaultauth.service;

import dev.vaultauth.model.TotpSecret;
import dev.vaultauth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TotpSecretRepository extends JpaRepository<TotpSecret, Long> {
    Optional<TotpSecret> findByUser(User user);
    Optional<TotpSecret> findByUserAndConfirmedTrue(User user);
}
