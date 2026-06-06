package com.knappi.security.config;

import com.knappi.security.auth.AuthenticatedUserResponse;
import com.knappi.security.user.Role;
import com.knappi.security.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    private static final Duration SESSION_DURATION = Duration.ofHours(1);
    private final String secretKey;

    public JwtService(@Value("${app.jwt.secret}") String secretKey) {
        this.secretKey = secretKey;
    }


    public String extractUsername(String token) {
        return extractClaims(token, Claims::getSubject);
    }

    public <T> T extractClaims(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(buildSessionClaims(userDetails), userDetails);
    }

    public String generateToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails
    ) {
        Instant issuedAt = Instant.now();
        return Jwts
                .builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(issuedAt.plus(SESSION_DURATION)))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public AuthenticatedUserResponse extractSession(String token) {
        Claims claims = extractAllClaims(token);
        User user = buildUserFromClaims(claims);
        user.setPassword(null);

        return AuthenticatedUserResponse.builder()
                .user(user)
                .sessionExpiresAt(claims.getExpiration().toInstant())
                .sessionDurationSeconds(getSessionDuration().toSeconds())
                .build();
    }

    public User extractUser(String token) {
        User user = buildUserFromClaims(extractAllClaims(token));
        user.setPassword(null);
        return user;
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername())
                && !isTokenExpired(token)
                && isIssuedAfterPasswordChange(token, userDetails);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaims(token, Claims::getExpiration);
    }

    public Instant extractExpirationInstant(String token) {
        return extractExpiration(token).toInstant();
    }

    public Duration getSessionDuration() {
        return SESSION_DURATION;
    }

    private Date extractIssuedAt(String token) {
        return extractClaims(token, Claims::getIssuedAt);
    }

    private boolean isIssuedAfterPasswordChange(String token, UserDetails userDetails) {
        if (!(userDetails instanceof User user) || user.getPasswordChangedAt() == null) {
            return true;
        }

        Date issuedAt = extractIssuedAt(token);
        if (issuedAt == null) {
            return false;
        }

        Date passwordChangedAt = Date.from(
                user.getPasswordChangedAt()
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .truncatedTo(ChronoUnit.SECONDS)
        );

        return !issuedAt.before(passwordChangedAt);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Map<String, Object> buildSessionClaims(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();

        if (!(userDetails instanceof User user)) {
            return claims;
        }

        claims.put("userId", user.getId());
        claims.put("email", user.getEmail());
        claims.put("firstname", user.getFirstname());
        claims.put("lastname", user.getLastname());
        claims.put("role", user.getRole() == null ? null : user.getRole().name());
        claims.put("authProvider", user.getAuthProvider());
        claims.put("aureonVoiceId", user.getAureonVoiceId());
        claims.put(
                "passwordChangedAt",
                user.getPasswordChangedAt() == null ? null : user.getPasswordChangedAt().toString()
        );
        claims.values().removeIf(value -> value == null);
        return claims;
    }

    private User buildUserFromClaims(Claims claims) {
        String email = getStringClaim(claims, "email", claims.getSubject());
        String role = getStringClaim(claims, "role", Role.USER.name());

        return User.builder()
                .id(getLongClaim(claims, "userId"))
                .username(email)
                .firstname(getStringClaim(claims, "firstname", null))
                .lastname(getStringClaim(claims, "lastname", null))
                .email(email)
                .password(null)
                .role(parseRole(role))
                .authProvider(getStringClaim(claims, "authProvider", "LOCAL"))
                .aureonVoiceId(getStringClaim(claims, "aureonVoiceId", null))
                .passwordChangedAt(parseLocalDateTime(getStringClaim(claims, "passwordChangedAt", null)))
                .build();
    }

    private String getStringClaim(Claims claims, String name, String fallback) {
        Object value = claims.get(name);
        return value == null ? fallback : value.toString();
    }

    private Long getLongClaim(Claims claims, String name) {
        Object value = claims.get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Role parseRole(String role) {
        try {
            return Role.valueOf(role);
        } catch (RuntimeException ignored) {
            return Role.USER;
        }
    }

    private LocalDateTime parseLocalDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
