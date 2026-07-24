package com.seabaas.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class JwtUtils {
    // HS256 requires a key of at least 32 bytes (256 bits)
    private static final SecretKey KEY = Keys.hmacShaKeyFor(
            System.getenv("JWT_SECRET").getBytes()
    );

    public static String generate(String userId, Set<Role> roles) {
        return Jwts.builder()
                .subject(userId)
                .claim("roles", roles.stream().map(Enum::name).toList())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusMillis(86_400_000)))
                // 24h
                .signWith(KEY)
                .compact();
    }

    public static Set<Role> validate(String token) {
        Claims claims = parseClaims(token);
        List<?> rawList = claims.get("roles", List.class);
        if (rawList == null) return Set.of();
        return rawList.stream()
                .map(Object::toString)
                .map(Role::valueOf)
                .collect(Collectors.toSet());
    }

    public static String getUserId(String token) {
        return parseClaims(token).getSubject();
    }

    private static Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
