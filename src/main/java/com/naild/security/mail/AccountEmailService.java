package com.naild.security.mail;

import com.naild.security.user.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class AccountEmailService {

    private static final Logger logger = LoggerFactory.getLogger(AccountEmailService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.mode:log}")
    private String mailMode;

    @Value("${app.mail.from:no-reply@memotree.local}")
    private String fromAddress;

    public void sendPasswordResetEmail(User user, String resetLink, long expiryMinutes) {
        String subject = "Reset your Memotree password";
        String body = """
                Hi %s,

                We received a request to reset your Memotree password.

                Reset your password using this link:
                %s

                This link expires in %d minutes. If you did not request a password reset, you can ignore this email.
                """.formatted(displayName(user), resetLink, expiryMinutes);

        sendEmail(user.getEmail(), subject, body, "password reset");
    }

    public void sendPasswordChangedEmail(User user) {
        String subject = "Your Memotree password was changed";
        String body = """
                Hi %s,

                This is a confirmation that your Memotree password was changed on %s.

                If you did not make this change, reset your password immediately and contact support.
                """.formatted(displayName(user), LocalDateTime.now().format(TIMESTAMP_FORMATTER));

        sendEmail(user.getEmail(), subject, body, "password changed");
    }

    private void sendEmail(String to, String subject, String body, String emailType) {
        if (!"smtp".equalsIgnoreCase(mailMode)) {
            logEmail(to, subject, body, emailType);
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            logger.error("app.mail.mode is smtp but no mail sender is configured. Logging {} email instead.", emailType);
            logEmail(to, subject, body, emailType);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setFrom(fromAddress);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception ex) {
            logger.error("Failed to send {} email to {}", emailType, to, ex);
        }
    }

    private void logEmail(String to, String subject, String body, String emailType) {
        logger.info("Logging {} email to {}\nSubject: {}\n\n{}", emailType, to, subject, body);
    }

    private String displayName(User user) {
        if (user.getFirstname() != null && !user.getFirstname().isBlank()) {
            return user.getFirstname();
        }
        return "there";
    }
}
