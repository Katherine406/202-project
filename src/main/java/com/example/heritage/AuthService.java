package com.example.heritage;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private static final Map<String, DemoUser> USERS = Map.of(
            "contributor", new DemoUser(1001L, "contributor", "123456", UserRole.CONTRIBUTOR),
            "viewer", new DemoUser(2001L, "viewer", "123456", UserRole.VIEWER),
            "admin", new DemoUser(3001L, "admin", "123456", UserRole.ADMIN)
    );

    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();

    public LoginResponse login(LoginRequest request) {
        if (request == null || isBlank(request.username()) || isBlank(request.password())) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        DemoUser user = USERS.get(request.username());
        if (user == null || !user.password().equals(request.password())) {
            throw new IllegalStateException("用户名或密码错误");
        }
        String token = UUID.randomUUID().toString();
        UserSession session = new UserSession(user.userId(), user.username(), user.role());
        sessions.put(token, session);
        return new LoginResponse(token, session.userId(), session.username(), session.role());
    }

    public UserSession requireSession(String token) {
        if (isBlank(token)) {
            throw new IllegalStateException("请先登录");
        }
        UserSession session = sessions.get(token);
        if (session == null) {
            throw new IllegalStateException("登录已失效，请重新登录");
        }
        return session;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record DemoUser(Long userId, String username, String password, UserRole role) {
    }
}
