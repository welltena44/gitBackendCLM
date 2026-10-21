package com.suivicontrats.front.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwtService {
    private static final long TOKEN_LIFETIME_SECONDS = 30 * 60;
    private static final String HEADER = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final ObjectMapper objectMapper;

    @Value("${security.jwt.secret:}")
    private String secret;

    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("CLM_JWT_SECRET doit contenir au moins 32 caractères.");
        }
    }

    public String issue(Integer userId) {
        try {
            long now = Instant.now().getEpochSecond();
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    objectMapper.writeValueAsBytes(Map.of("sub", userId, "iat", now, "exp", now + TOKEN_LIFETIME_SECONDS)));
            String unsigned = HEADER + "." + payload;
            return unsigned + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(unsigned));
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de créer le jeton de session.", e);
        }
    }

    public Integer verifyAndGetUserId(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !constantTimeEquals(sign(parts[0] + "." + parts[1]), Base64.getUrlDecoder().decode(parts[2]))) {
                return null;
            }
            Map<?, ?> claims = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            long expiresAt = ((Number) claims.get("exp")).longValue();
            long userId = ((Number) claims.get("sub")).longValue();
            if (expiresAt <= Instant.now().getEpochSecond() || userId <= 0 || userId > Integer.MAX_VALUE) return null;
            return (int) userId;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
    }

    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
        return diff == 0;
    }
}
