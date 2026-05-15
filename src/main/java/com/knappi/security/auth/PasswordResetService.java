package com.knappi.security.auth;

import com.knappi.security.mail.AccountEmailService;
import com.knappi.security.user.User;
import com.knappi.security.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountEmailService accountEmailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.password-reset.base-url:http://localhost:3000/reset-password}")
    private String passwordResetBaseUrl;

    @Value("${app.password-reset.expiry-minutes:60}")
    private long passwordResetExpiryMinutes;

    public void requestPasswordReset(ForgotPasswordRequest request) {
        if (request == null || !StringUtils.hasText(request.getEmail())) {
            return;
        }

        userRepository.findByEmailIgnoreCase(request.getEmail().trim())
                .filter(this::isLocalAccount)
                .ifPresent(user -> {
                    String rawToken = generateRawToken();
                    user.setPasswordResetTokenHash(hashToken(rawToken));
                    user.setPasswordResetTokenExpiresAt(LocalDateTime.now().plusMinutes(passwordResetExpiryMinutes));
                    userRepository.save(user);

                    String resetLink = UriComponentsBuilder.fromUriString(passwordResetBaseUrl)
                            .queryParam("token", rawToken)
                            .build()
                            .toUriString();
                    accountEmailService.sendPasswordResetEmail(user, resetLink, passwordResetExpiryMinutes);
                });
    }

    public void resetPassword(ResetPasswordRequest request) {
        if (request == null || !StringUtils.hasText(request.getToken()) || !StringUtils.hasText(request.getPassword())) {
            throw new PasswordResetException("Reset token and password are required.");
        }

        User user = userRepository.findByPasswordResetTokenHash(hashToken(request.getToken().trim()))
                .orElseThrow(() -> new PasswordResetException("Invalid or expired reset token."));

        if (!isLocalAccount(user)) {
            clearResetToken(user);
            userRepository.save(user);
            throw new PasswordResetException("Password reset is not available for social login accounts.");
        }

        if (user.getPasswordResetTokenExpiresAt() == null || user.getPasswordResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            clearResetToken(user);
            userRepository.save(user);
            throw new PasswordResetException("Invalid or expired reset token.");
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.markPasswordChangedNow();
        clearResetToken(user);
        userRepository.save(user);
        accountEmailService.sendPasswordChangedEmail(user);
    }

    private boolean isLocalAccount(User user) {
        String authProvider = user.getAuthProvider();
        if (authProvider == null) {
            return user.getPassword() == null || !user.getPassword().startsWith("oauth-user");
        }

        return "LOCAL".equalsIgnoreCase(authProvider);
    }

    private void clearResetToken(User user) {
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetTokenExpiresAt(null);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
