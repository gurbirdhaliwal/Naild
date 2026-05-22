package com.knappi.security.auth;

import com.knappi.security.config.JwtService;
import com.knappi.security.mail.AccountEmailService;
import com.knappi.security.user.Role;
import com.knappi.security.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.knappi.security.user.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AccountEmailService accountEmailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.login-verification.expiry-minutes:10}")
    private long loginVerificationExpiryMinutes;

    private static final int MAX_LOGIN_VERIFICATION_ATTEMPTS = 5;

    public AuthenticationResponse register(RegisterRequest request) {
        var user = User.builder().
                 username(request.getFirstname())
                .firstname(request.getFirstname())
                .lastname(request.getLastname())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .authProvider("LOCAL")
                .build();
        userRepository.save(user);
        var jwtToken = jwtService.generateToken(user);
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }

    public void authenticate(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow();
        var code = generateLoginVerificationCode();
        user.setLoginVerificationCodeHash(passwordEncoder.encode(code));
        user.setLoginVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(loginVerificationExpiryMinutes));
        user.setLoginVerificationAttempts(0);
        userRepository.save(user);
        accountEmailService.sendLoginVerificationCodeEmail(user, code, loginVerificationExpiryMinutes);
    }

    public AuthenticationResponse verifyLoginCode(LoginVerificationRequest request) {
        if (request == null || !StringUtils.hasText(request.getEmail()) || !StringUtils.hasText(request.getCode())) {
            throw new LoginVerificationException("Email and verification code are required.");
        }

        var user = userRepository.findByEmailIgnoreCase(request.getEmail().trim())
                .orElseThrow(() -> new LoginVerificationException("Invalid or expired verification code."));

        if (user.getLoginVerificationCodeHash() == null
                || user.getLoginVerificationCodeExpiresAt() == null
                || user.getLoginVerificationCodeExpiresAt().isBefore(LocalDateTime.now())) {
            clearLoginVerificationCode(user);
            userRepository.save(user);
            throw new LoginVerificationException("Invalid or expired verification code.");
        }

        int attempts = user.getLoginVerificationAttempts() == null ? 0 : user.getLoginVerificationAttempts();
        if (attempts >= MAX_LOGIN_VERIFICATION_ATTEMPTS) {
            clearLoginVerificationCode(user);
            userRepository.save(user);
            throw new LoginVerificationException("Too many incorrect verification attempts. Sign in again to get a new code.");
        }

        if (!passwordEncoder.matches(request.getCode().trim(), user.getLoginVerificationCodeHash())) {
            int updatedAttempts = attempts + 1;
            if (updatedAttempts >= MAX_LOGIN_VERIFICATION_ATTEMPTS) {
                clearLoginVerificationCode(user);
            } else {
                user.setLoginVerificationAttempts(updatedAttempts);
            }
            userRepository.save(user);
            throw new LoginVerificationException("Invalid or expired verification code.");
        }

        clearLoginVerificationCode(user);
        userRepository.save(user);
        var jwtToken = jwtService.generateToken(user);
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }

    private void clearLoginVerificationCode(User user) {
        user.setLoginVerificationCodeHash(null);
        user.setLoginVerificationCodeExpiresAt(null);
        user.setLoginVerificationAttempts(null);
    }

    private String generateLoginVerificationCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}
