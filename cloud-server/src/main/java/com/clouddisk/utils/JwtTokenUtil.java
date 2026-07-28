package com.clouddisk.utils;

import com.clouddisk.common.AuthenticationException;
import com.clouddisk.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenUtil {

    private static final String USER_ID_CLAIM = "userId";

    private final JwtProperties jwtProperties;

    public JwtTokenUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String generateToken(Long userId) {
        Instant issuedAt = Instant.now();
        Instant expirationAt = issuedAt.plusSeconds(jwtProperties.getExpirationSeconds());

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(USER_ID_CLAIM, userId)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expirationAt))
                .signWith(signingKey())
                .compact();
    }

    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Object userId = claims.get(USER_ID_CLAIM);
            if (userId instanceof Number number) {
                return number.longValue();
            }

            throw new AuthenticationException("Invalid or expired token");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AuthenticationException("Invalid or expired token", ex);
        }
    }

    private SecretKey signingKey() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
