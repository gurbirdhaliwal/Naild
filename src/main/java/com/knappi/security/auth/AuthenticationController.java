package com.knappi.security.auth;

import com.knappi.security.config.JwtService;
import com.knappi.security.user.User;
import com.knappi.security.user.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService service;
    private final PasswordResetService passwordResetService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Value("${app.auth.cookie-secure:true}")
    private boolean cookieSecure;

    @Value("${app.auth.cookie-same-site:None}")
    private String cookieSameSite;

    @Value("${app.auth.cookie-domain:}")
    private String cookieDomain;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request, HttpServletResponse response) {
        var authResponse = service.register(request);
        addAuthCookie(response, authResponse.getToken());
        return ResponseEntity.ok(Map.of(
                "message", "Registered Successfully",
                "sessionExpiresAt", jwtService.extractExpirationInstant(authResponse.getToken()),
                "sessionDurationSeconds", jwtService.getSessionDuration().toSeconds()
        ));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<?> authenticate(@RequestBody AuthenticationRequest request, HttpServletResponse response) {
        service.authenticate(request);
        return ResponseEntity.accepted().body(Map.of(
                "message", "Verification code sent.",
                "requiresEmailVerification", true
        ));
    }

    @PostMapping("/authenticate/verify")
    public ResponseEntity<?> verifyAuthenticationCode(@RequestBody LoginVerificationRequest request, HttpServletResponse response) {
        try {
            var authResponse = service.verifyLoginCode(request);
            addAuthCookie(response, authResponse.getToken());
            return ResponseEntity.ok(Map.of(
                    "message", "Authenticated Successfully",
                    "sessionExpiresAt", jwtService.extractExpirationInstant(authResponse.getToken()),
                    "sessionDurationSeconds", jwtService.getSessionDuration().toSeconds()
            ));
        } catch (LoginVerificationException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildDeleteCookie(false).toString());

        if (hasCookieDomain()) {
            response.addHeader(HttpHeaders.SET_COOKIE, buildDeleteCookie(true).toString());
        }

        return ResponseEntity.ok("Logout Successful");
    }

    private ResponseCookie buildDeleteCookie(boolean includeDomain) {
        ResponseCookie.ResponseCookieBuilder deleteCookie = ResponseCookie.from("token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite(cookieSameSite);
        if (includeDomain) {
            applyCookieDomain(deleteCookie);
        }
        return deleteCookie.build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestPasswordReset(request);
        return ResponseEntity.ok(Map.of(
                "message",
                "If an account exists for that email, a password reset link has been sent."
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            passwordResetService.resetPassword(request);
            return ResponseEntity.ok(Map.of("message", "Password reset successfully."));
        } catch (PasswordResetException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/csrf")
    public ResponseEntity<?> getCsrfToken(CsrfToken csrfToken) {
        return ResponseEntity.ok(Map.of("token", csrfToken.getToken()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) {
            return ResponseEntity.badRequest().body("Missing authentication token");
        }

        try {
            String email = jwtService.extractUsername(token);
            User user = userRepository.findByEmail(email).orElseThrow();

            if (!jwtService.isTokenValid(token, user)) {
                return ResponseEntity.status(401).body("Invalid or expired token");
            }

            // Optional: avoid sending password field back
            user.setPassword(null);

            return ResponseEntity.ok(AuthenticatedUserResponse.builder()
                    .user(user)
                    .sessionExpiresAt(jwtService.extractExpirationInstant(token))
                    .sessionDurationSeconds(jwtService.getSessionDuration().toSeconds())
                    .build());
        } catch (Exception ex) {
            return ResponseEntity.status(401).body("Invalid or expired token");
        }
    }

    private void addAuthCookie(HttpServletResponse response, String token) {
        if (hasCookieDomain()) {
            response.addHeader(HttpHeaders.SET_COOKIE, buildDeleteCookie(false).toString());
        }

        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from("token", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(jwtService.getSessionDuration())
                .sameSite(cookieSameSite);
        if (hasCookieDomain()) {
            applyCookieDomain(cookie);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }

    private void applyCookieDomain(ResponseCookie.ResponseCookieBuilder cookieBuilder) {
        cookieBuilder.domain(getCookieDomain());
    }

    private boolean hasCookieDomain() {
        return !getCookieDomain().isBlank();
    }

    private String getCookieDomain() {
        String domain = cookieDomain == null ? "" : cookieDomain.trim();
        return domain;
    }

    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }

        if (request.getCookies() == null) {
            return null;
        }

        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if ("token".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}
