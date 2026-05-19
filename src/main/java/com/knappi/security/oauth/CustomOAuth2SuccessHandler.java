package com.knappi.security.oauth;

import com.knappi.security.config.JwtService;
import com.knappi.security.user.User;
import com.knappi.security.user.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Value("${app.oauth.success-redirect-url:http://localhost:3000/interview}")
    private String successRedirectUrl;

    @Value("${app.auth.cookie-secure:true}")
    private boolean cookieSecure;

    @Value("${app.auth.cookie-same-site:None}")
    private String cookieSameSite;

    @Value("${app.auth.cookie-domain:}")
    private String cookieDomain;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found after Google login"));

        String jwt = jwtService.generateToken(user);
        String domain = cookieDomain == null ? "" : cookieDomain.trim();

        if (!domain.isBlank()) {
            ResponseCookie legacyDeleteCookie = ResponseCookie.from("token", "")
                    .httpOnly(true)
                    .secure(cookieSecure)
                    .path("/")
                    .maxAge(0)
                    .sameSite(cookieSameSite)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, legacyDeleteCookie.toString());
        }

        ResponseCookie.ResponseCookieBuilder jwtCookie = ResponseCookie.from("token", jwt)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(jwtService.getSessionDuration())
                .sameSite(cookieSameSite);

        if (!domain.isBlank()) {
            jwtCookie.domain(domain);
        }

        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.build().toString());
        response.sendRedirect(successRedirectUrl);
    }
}
