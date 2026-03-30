package dev.vaultauth.service;

import dev.vaultauth.model.SessionEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionAuditService {

    private final SessionEventRepository sessionEventRepository;

    public void log(String username, SessionEvent.EventType eventType,
                    HttpServletRequest request, String detail) {
        SessionEvent event = SessionEvent.builder()
                .username(username)
                .eventType(eventType)
                .ipAddress(extractIp(request))
                .userAgent(request != null ? request.getHeader("User-Agent") : null)
                .detail(detail)
                .build();
        sessionEventRepository.save(event);
    }

    public void log(String username, SessionEvent.EventType eventType, String detail) {
        log(username, eventType, null, detail);
    }

    public Page<SessionEvent> getAllEvents(Pageable pageable) {
        return sessionEventRepository.findAllByOrderByOccurredAtDesc(pageable);
    }

    public Page<SessionEvent> getEventsByUser(String username, Pageable pageable) {
        return sessionEventRepository.findByUsernameOrderByOccurredAtDesc(username, pageable);
    }

    private String extractIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
