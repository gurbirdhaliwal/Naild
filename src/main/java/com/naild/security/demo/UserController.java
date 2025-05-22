package com.naild.security.demo;

import com.naild.security.auth.RegisterRequest;
import com.naild.security.auth.UpdateUserRequest;
import com.naild.security.user.User;
import com.naild.security.user.UserRepository;
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

    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(Authentication authentication) {
        String email = ((UserDetails) authentication.getPrincipal()).getUsername();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
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

        if (request.getFirstname() != null) user.setFirstname(request.getFirstname());
        if (request.getLastname() != null) user.setLastname(request.getLastname());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getPassword() != null) {
         if (request.getCurrentpassword() == null || request.getCurrentpassword().isBlank()) {
             return ResponseEntity.badRequest().body("Current password is required to change passwords");
         } else if (!passwordEncoder.matches(request.getCurrentpassword(), user.getPassword())) {
             return ResponseEntity.badRequest().body("Current password is incorrect");
         }
         user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        userRepository.save(user);
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }
}

