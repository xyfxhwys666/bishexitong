package com.xyfxhwys.faceprivacy.auth;

public record LoginView(
        String token,
        long expiresInSeconds,
        UserView user
) {
    public record UserView(String username, String role) {
        public static UserView from(AuthSessionService.Session session) {
            return new UserView(session.username(), session.role());
        }
    }
}
