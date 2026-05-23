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
import org.springframework.transaction.annotation.Transactional;
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
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.login-verification.expiry-minutes:10}")
    private long loginVerificationExpiryMinutes;

    private static final int MAX_LOGIN_VERIFICATION_ATTEMPTS = 5;

    public void register(RegisterRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getFirstname())
                || !StringUtils.hasText(request.getLastname())
                || !StringUtils.hasText(request.getEmail())
                || !StringUtils.hasText(request.getPassword())) {
            throw new LoginVerificationException("All signup fields are required.");
        }

        String email = request.getEmail().trim();
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new LoginVerificationException("An account already exists for this email.");
        }

        String code = generateLoginVerificationCode();
        PendingRegistration pendingRegistration = pendingRegistrationRepository.findByEmailIgnoreCase(email)
                .orElseGet(PendingRegistration::new);
        pendingRegistration.setFirstname(request.getFirstname().trim());
        pendingRegistration.setLastname(request.getLastname().trim());
        pendingRegistration.setEmail(email);
        pendingRegistration.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        pendingRegistration.setVerificationCodeHash(passwordEncoder.encode(code));
        pendingRegistration.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(loginVerificationExpiryMinutes));
        pendingRegistration.setVerificationAttempts(0);
        pendingRegistrationRepository.save(pendingRegistration);

        accountEmailService.sendRegistrationVerificationCodeEmail(
                email,
                pendingRegistration.getFirstname(),
                code,
                loginVerificationExpiryMinutes
        );
    }

    @Transactional
    public AuthenticationResponse verifyRegistrationCode(LoginVerificationRequest request) {
        if (request == null || !StringUtils.hasText(request.getEmail()) || !StringUtils.hasText(request.getCode())) {
            throw new LoginVerificationException("Email and verification code are required.");
        }

        String email = request.getEmail().trim();
        var pendingRegistration = pendingRegistrationRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new LoginVerificationException("Invalid or expired verification code."));

        if (pendingRegistration.getVerificationCodeExpiresAt() == null
                || pendingRegistration.getVerificationCodeExpiresAt().isBefore(LocalDateTime.now())) {
            pendingRegistrationRepository.delete(pendingRegistration);
            throw new LoginVerificationException("Invalid or expired verification code.");
        }

        int attempts = pendingRegistration.getVerificationAttempts() == null
                ? 0
                : pendingRegistration.getVerificationAttempts();
        if (attempts >= MAX_LOGIN_VERIFICATION_ATTEMPTS) {
            pendingRegistrationRepository.delete(pendingRegistration);
            throw new LoginVerificationException("Too many incorrect verification attempts. Sign up again to get a new code.");
        }

        if (!passwordEncoder.matches(request.getCode().trim(), pendingRegistration.getVerificationCodeHash())) {
            int updatedAttempts = attempts + 1;
            if (updatedAttempts >= MAX_LOGIN_VERIFICATION_ATTEMPTS) {
                pendingRegistrationRepository.delete(pendingRegistration);
            } else {
                pendingRegistration.setVerificationAttempts(updatedAttempts);
                pendingRegistrationRepository.save(pendingRegistration);
            }
            throw new LoginVerificationException("Invalid or expired verification code.");
        }

        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            pendingRegistrationRepository.delete(pendingRegistration);
            throw new LoginVerificationException("An account already exists for this email.");
        }

        var user = User.builder()
                .username(pendingRegistration.getFirstname())
                .firstname(pendingRegistration.getFirstname())
                .lastname(pendingRegistration.getLastname())
                .email(pendingRegistration.getEmail())
                .password(pendingRegistration.getPasswordHash())
                .role(Role.USER)
                .authProvider("LOCAL")
                .build();
        userRepository.save(user);
        pendingRegistrationRepository.delete(pendingRegistration);

        var jwtToken = jwtService.generateToken(user);
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow();
        clearLoginVerificationCode(user);
        userRepository.save(user);
        var jwtToken = jwtService.generateToken(user);
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
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
