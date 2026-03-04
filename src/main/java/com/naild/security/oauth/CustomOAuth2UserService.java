package com.naild.security.oauth;

import com.naild.security.user.Role;
import com.naild.security.user.User;
import com.naild.security.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User user = new DefaultOAuth2UserService().loadUser(request);

        String email = user.getAttribute("email");
        String firstName = user.getAttribute("given_name");
        String lastName = user.getAttribute("family_name");
        String registrationId = request.getClientRegistration().getRegistrationId();
        String authProvider = registrationId == null
                ? "OAUTH"
                : registrationId.toUpperCase(Locale.ROOT);

        userRepository.findByEmail(email).map(existingUser -> {
            // Backfill provider for legacy OAuth users without touching local-auth accounts.
            if (existingUser.getAuthProvider() == null) {
                String existingPassword = existingUser.getPassword();
                if (existingPassword != null && existingPassword.startsWith("oauth-user")) {
                    existingUser.setAuthProvider(authProvider);
                    return userRepository.save(existingUser);
                }
            }
            return existingUser;
        }).orElseGet(() -> {
            User newUser = User.builder()
                    .email(email)
                    .firstname(firstName)
                    .lastname(lastName)
                    .username(firstName)
                    .role(Role.USER)
                    .authProvider(authProvider)
                    .password("oauth-user-" + UUID.randomUUID())
                    .build();
            return userRepository.save(newUser);
        });

        return user;
    }
}
