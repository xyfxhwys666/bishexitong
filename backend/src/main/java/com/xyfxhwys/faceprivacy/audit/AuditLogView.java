package com.xyfxhwys.faceprivacy.audit;

import java.time.Instant;

public record AuditLogView(
        Long id,
        String username,
        String role,
        String action,
        String targetType,
        String targetId,
        String detail,
        Instant createdAt
) {
    public static AuditLogView from(AuditLog log) {
        return new AuditLogView(
                log.getId(),
                log.getUsername(),
                log.getRole(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getDetail(),
                log.getCreatedAt()
        );
    }
}
