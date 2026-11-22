package com.xyfxhwys.faceprivacy.auth;

import com.xyfxhwys.faceprivacy.audit.AuditLogService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthSessionService {

    public static final String ADMIN_ROLE = "ADMIN";
    public static final String USER_ROLE = "USER";
    private static final long SESSION_SECONDS = Duration.ofHours(8).toSeconds();

    private final UserAccountRepository repository;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AuthSessionService(UserAccountRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @PostConstruct
    void seedDemoUsers() {
        createIfMissing("admin", "admin123", ADMIN_ROLE);
        createIfMissing("student", "student123", USER_ROLE);
    }

    public LoginView login(String username, String password) {
        String normalizedUsername = username == null ? "" : username.trim();
        UserAccount account = repository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new UnauthorizedException("用户名或密码错误"));
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        String token = UUID.randomUUID().toString();
        Session session = new Session(normalizedUsername, account.getRole(), Instant.now().plusSeconds(SESSION_SECONDS));
        sessions.put(token, session);
        auditLogService.record(session, "LOGIN", "AUTH", null, "用户登录");
        return new LoginView(token, SESSION_SECONDS, LoginView.UserView.from(session));
    }

    public Session require(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException("请先登录");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        Session session = sessions.get(token);
        if (session == null || session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            throw new UnauthorizedException("登录已过期，请重新登录");
        }
        return session;
    }

    public void logout(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring("Bearer ".length()).trim();
            Session session = sessions.remove(token);
            if (session != null) {
                auditLogService.record(session, "LOGOUT", "AUTH", null, "用户退出登录");
            }
        }
    }

    private void createIfMissing(String username, String password, String role) {
        if (!repository.existsByUsername(username)) {
            repository.save(new UserAccount(username, passwordEncoder.encode(password), role));
        }
    }

    public record Session(String username, String role, Instant expiresAt) {
        public boolean isAdmin() {
            return ADMIN_ROLE.equals(role);
        }
    }
}
