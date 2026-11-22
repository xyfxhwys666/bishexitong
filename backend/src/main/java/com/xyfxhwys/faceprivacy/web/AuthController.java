package com.xyfxhwys.faceprivacy.web;

import com.xyfxhwys.faceprivacy.auth.AuthSessionService;
import com.xyfxhwys.faceprivacy.auth.LoginRequest;
import com.xyfxhwys.faceprivacy.auth.LoginView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthSessionService authSessionService;

    public AuthController(AuthSessionService authSessionService) {
        this.authSessionService = authSessionService;
    }

    @PostMapping("/login")
    public LoginView login(@Valid @RequestBody LoginRequest request) {
        return authSessionService.login(request.username(), request.password());
    }

    @GetMapping("/me")
    public LoginView.UserView me(HttpServletRequest request) {
        return LoginView.UserView.from(authSessionService.require(request));
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request) {
        authSessionService.logout(request);
        return Map.of("message", "已退出登录");
    }
}
