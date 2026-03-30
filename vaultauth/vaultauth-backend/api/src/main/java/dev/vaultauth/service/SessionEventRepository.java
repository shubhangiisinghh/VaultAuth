package dev.vaultauth.service;

import dev.vaultauth.model.SessionEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionEventRepository extends JpaRepository<SessionEvent, Long> {
    Page<SessionEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);
    Page<SessionEvent> findByUsernameOrderByOccurredAtDesc(String username, Pageable pageable);
}
