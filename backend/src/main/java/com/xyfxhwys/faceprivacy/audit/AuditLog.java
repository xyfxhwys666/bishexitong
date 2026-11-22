package com.xyfxhwys.faceprivacy.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(length = 40)
    private String targetType;

    @Column(length = 64)
    private String targetId;

    @Column(length = 500)
    private String detail;

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(
            String username,
            String role,
            String action,
            String targetType,
            String targetId,
            String detail
    ) {
        this.username = username;
        this.role = role;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
