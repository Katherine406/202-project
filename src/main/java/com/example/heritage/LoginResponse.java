package com.example.heritage;

public record LoginResponse(String token, Long userId, String username, UserRole role) {
}
