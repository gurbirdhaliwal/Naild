package com.knappi.security.auth;

import com.knappi.security.config.JwtService;
import com.knappi.security.user.Role;
import com.knappi.security.user.User;
import com.knappi.security.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationControllerTest {

    private static final String JWT = "test.jwt.value";

    private AuthenticationService authenticationService;
    private JwtService jwtService;
    private UserRepository userRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authenticationService = mock(AuthenticationService.class);
        jwtService = mock(JwtService.class);
        userRepository = mock(UserRepository.class);

        AuthenticationController controller = new AuthenticationController(
                authenticationService,
                mock(PasswordResetService.class),
                jwtService,
                userRepository
        );
        ReflectionTestUtils.setField(controller, "cookieSecure", true);
        ReflectionTestUtils.setField(controller, "cookieSameSite", "Lax");
        ReflectionTestUtils.setField(controller, "cookieDomain", ".knappi.ca");

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(jwtService.extractExpirationInstant(JWT)).thenReturn(Instant.parse("2026-05-19T12:00:00Z"));
        when(jwtService.getSessionDuration()).thenReturn(Duration.ofHours(8));
    }

    @Test
    void meUsesStatelessJwtSessionByDefault() throws Exception {
        User user = localUser();
        when(jwtService.extractSession(JWT)).thenReturn(AuthenticatedUserResponse.builder()
                .user(user)
                .sessionExpiresAt(Instant.parse("2026-05-19T12:00:00Z"))
                .sessionDurationSeconds(Duration.ofHours(8).toSeconds())
                .build());

        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(new jakarta.servlet.http.Cookie("token", JWT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(user.getEmail()))
                .andExpect(jsonPath("$.sessionDurationSeconds").value(Duration.ofHours(8).toSeconds()));

        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void meCanRequireDbValidationWhenConfigured() throws Exception {
        User user = localUser();
        when(jwtService.extractUsername(JWT)).thenReturn(user.getEmail());
        when(jwtService.isTokenValid(JWT, user)).thenReturn(true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        AuthenticationController controller = new AuthenticationController(
                authenticationService,
                mock(PasswordResetService.class),
                jwtService,
                userRepository
        );
        ReflectionTestUtils.setField(controller, "requireDbValidationForMe", true);
        MockMvc strictMockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        strictMockMvc.perform(get("/api/v1/auth/me")
                        .cookie(new jakarta.servlet.http.Cookie("token", JWT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(user.getEmail()));

        verify(userRepository).findByEmail(user.getEmail());
    }

    @Test
    void registerSendsVerificationCodeWithoutSettingCookie() throws Exception {
        String body = """
                {"firstname":"Test","lastname":"User","email":"user@example.com","password":"password"}
                """;

        List<String> setCookies = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requiresEmailVerification").value(true))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn()
                .getResponse()
                .getHeaders(HttpHeaders.SET_COOKIE);

        assertThat(setCookies).isEmpty();
    }

    @Test
    void verifyRegistrationCodeSetsHttpOnlyCookieWithoutReturningJwtInJson() throws Exception {
        when(authenticationService.verifyRegistrationCode(any(LoginVerificationRequest.class)))
                .thenReturn(AuthenticationResponse.builder().token(JWT).build());

        String body = """
                {"email":"user@example.com","code":"123456"}
                """;

        List<String> setCookies = mockMvc.perform(post("/api/v1/auth/register/verify")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(cookie().httpOnly("token", true))
                .andExpect(cookie().secure("token", true))
                .andReturn()
                .getResponse()
                .getHeaders(HttpHeaders.SET_COOKIE);

        assertThat(setCookies).anySatisfy((setCookie) -> {
            assertThat(setCookie).contains("Domain=.knappi.ca");
            assertThat(setCookie).contains("SameSite=Lax");
        });
    }

    @Test
    void authenticateSetsHttpOnlyCookieWithoutReturningJwtInJson() throws Exception {
        when(authenticationService.authenticate(any(AuthenticationRequest.class)))
                .thenReturn(AuthenticationResponse.builder().token(JWT).build());

        String body = """
                {"email":"user@example.com","password":"password"}
                """;

        List<String> setCookies = mockMvc.perform(post("/api/v1/auth/authenticate")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(cookie().httpOnly("token", true))
                .andExpect(cookie().secure("token", true))
                .andReturn()
                .getResponse()
                .getHeaders(HttpHeaders.SET_COOKIE);

        assertThat(setCookies).anySatisfy((setCookie) -> {
            assertThat(setCookie).contains("Domain=.knappi.ca");
            assertThat(setCookie).contains("SameSite=Lax");
        });
    }

    @Test
    void verifyAuthenticationCodeSetsHttpOnlyCookieWithoutReturningJwtInJson() throws Exception {
        when(authenticationService.verifyLoginCode(any(LoginVerificationRequest.class)))
                .thenReturn(AuthenticationResponse.builder().token(JWT).build());

        String body = """
                {"email":"user@example.com","code":"123456"}
                """;

        List<String> setCookies = mockMvc.perform(post("/api/v1/auth/authenticate/verify")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(cookie().httpOnly("token", true))
                .andExpect(cookie().secure("token", true))
                .andReturn()
                .getResponse()
                .getHeaders(HttpHeaders.SET_COOKIE);

        assertThat(setCookies).anySatisfy((setCookie) -> {
            assertThat(setCookie).contains("Domain=.knappi.ca");
            assertThat(setCookie).contains("SameSite=Lax");
        });
    }

    private User localUser() {
        return User.builder()
                .id(1L)
                .username("user@example.com")
                .firstname("Test")
                .lastname("User")
                .email("user@example.com")
                .password("encoded-password")
                .role(Role.USER)
                .authProvider("LOCAL")
                .build();
    }
}
