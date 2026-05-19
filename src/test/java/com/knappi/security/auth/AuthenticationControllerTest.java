package com.knappi.security.auth;

import com.knappi.security.config.JwtService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationControllerTest {

    private static final String JWT = "test.jwt.value";

    private AuthenticationService authenticationService;
    private JwtService jwtService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authenticationService = mock(AuthenticationService.class);
        jwtService = mock(JwtService.class);

        AuthenticationController controller = new AuthenticationController(
                authenticationService,
                mock(PasswordResetService.class),
                jwtService,
                mock(UserRepository.class)
        );
        ReflectionTestUtils.setField(controller, "cookieSecure", true);
        ReflectionTestUtils.setField(controller, "cookieSameSite", "Lax");
        ReflectionTestUtils.setField(controller, "cookieDomain", ".knappi.ca");

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(jwtService.extractExpirationInstant(JWT)).thenReturn(Instant.parse("2026-05-19T12:00:00Z"));
        when(jwtService.getSessionDuration()).thenReturn(Duration.ofHours(8));
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
}
