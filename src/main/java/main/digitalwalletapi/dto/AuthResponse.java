package main.digitalwalletapi.dto;
public record AuthResponse(String token, String tokenType, long expiresIn, Long userId, Long walletId) {}
