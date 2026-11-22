package com.xyfxhwys.faceprivacy.audit;

import com.xyfxhwys.faceprivacy.auth.AuthSessionService;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(
            AuthSessionService.Session session,
            String action,
            String targetType,
            String targetId,
            String detail
    ) {
        repository.save(new AuditLog(
                session.username(),
                session.role(),
                action,
                targetType,
                targetId,
                detail
        ));
    }

    public void record(String username, String role, String action, String detail) {
        repository.save(new AuditLog(username, role, action, null, null, detail));
    }

    public List<AuditLogView> recent() {
        return repository.findTop30ByOrderByCreatedAtDesc().stream()
                .map(AuditLogView::from)
                .toList();
    }
}
