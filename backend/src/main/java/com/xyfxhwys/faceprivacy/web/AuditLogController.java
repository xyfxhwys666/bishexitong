package com.xyfxhwys.faceprivacy.web;

import com.xyfxhwys.faceprivacy.audit.AuditLogService;
import com.xyfxhwys.faceprivacy.audit.AuditLogView;
import com.xyfxhwys.faceprivacy.auth.AuthSessionService;
import com.xyfxhwys.faceprivacy.auth.ForbiddenException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final AuthSessionService authSessionService;

    public AuditLogController(AuditLogService auditLogService, AuthSessionService authSessionService) {
        this.auditLogService = auditLogService;
        this.authSessionService = authSessionService;
    }

    @GetMapping
    public List<AuditLogView> recent(HttpServletRequest request) {
        AuthSessionService.Session session = authSessionService.require(request);
        if (!session.isAdmin()) {
            throw new ForbiddenException("仅管理员可以查看操作审计日志");
        }
        return auditLogService.recent();
    }
}
