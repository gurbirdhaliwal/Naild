package com.knappi.security.demo;

import com.knappi.security.auth.UpdateUserRequest;
import com.knappi.security.mail.AccountEmailService;
import com.knappi.security.user.User;
import com.knappi.security.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountEmailService accountEmailService;

    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(Authentication authentication) {
        String email = ((UserDetails) authentication.getPrincipal()).getUsername();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getAuthProvider() == null) {
            String password = user.getPassword();
            if (password != null && password.startsWith("oauth-user")) {
                user.setAuthProvider("OAUTH");
                userRepository.save(user);
            }
        }

        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteUser(Authentication authentication) {
        String email = ((UserDetails) authentication.getPrincipal()).getUsername();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        userRepository.delete(user);

        return ResponseEntity.ok("User account deleted successfully");
    }

    @PatchMapping("/update")
    public ResponseEntity<?> updateUser(Authentication authentication, @RequestBody UpdateUserRequest request) {
        String email = ((UserDetails) authentication.getPrincipal()).getUsername();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean passwordProvided = request.getPassword() != null && !request.getPassword().isBlank();
        boolean isOauthAccount = isOauthAccount(user);

        if (request.getFirstname() != null) user.setFirstname(request.getFirstname());
        if (request.getLastname() != null) user.setLastname(request.getLastname());

        if (passwordProvided) {
            if (isOauthAccount) {
                return ResponseEntity.badRequest().body("Password changes are not available for social login accounts");
            }

            if (request.getCurrentpassword() == null || request.getCurrentpassword().isBlank()) {
                return ResponseEntity.badRequest().body("Current password is required to change passwords");
            }

            if (!passwordEncoder.matches(request.getCurrentpassword(), user.getPassword())) {
                return ResponseEntity.badRequest().body("Current password is incorrect");
            }

            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.markPasswordChangedNow();
        }

        userRepository.save(user);
        if (passwordProvided) {
            accountEmailService.sendPasswordChangedEmail(user);
        }
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    private boolean isOauthAccount(User user) {
        String authProvider = user.getAuthProvider();
        if (authProvider != null && !authProvider.equalsIgnoreCase("LOCAL")) {
            return true;
        }

        String password = user.getPassword();
        return password != null && password.startsWith("oauth-user");
    }
}
