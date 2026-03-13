package com.naild.security.interview;

import com.naild.security.config.JwtService;
import com.naild.security.user.User;
import com.naild.security.user.UserRepository;
import com.naild.security.user.Role;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/interviews")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"}, allowCredentials = "true")
public class InterviewController {
    
    private final InterviewService interviewService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    
    @GetMapping
    public ResponseEntity<?> getUserInterviews(HttpServletRequest request) {
        try {
            User user = getCurrentUser(request);
            System.out.println("Loading interviews for user: " + user.getEmail());
            List<Interview> interviews = interviewService.getUserInterviews(user);
            System.out.println("Found " + interviews.size() + " interviews");
            return ResponseEntity.ok(interviews);
        } catch (Exception e) {
            System.err.println("Error loading interviews: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<?> getInterviewById(@PathVariable Long id, HttpServletRequest request) {
        try {
            User user = getCurrentUser(request);
            Optional<Interview> interview = interviewService.getInterviewById(id, user);
            if (interview.isPresent()) {
                return ResponseEntity.ok(interview.get());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping
    public ResponseEntity<?> createInterview(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        try {
            System.out.println("Received create interview request: " + request);
            User user = getCurrentUser(httpRequest);
            System.out.println("User: " + user.getEmail());
            
            String title = (String) request.get("title");
            String role = (String) request.get("role");
            @SuppressWarnings("unchecked")
            List<String> languages = (List<String>) request.get("languages");
            String difficulty = (String) request.get("difficulty");
            @SuppressWarnings("unchecked")
            List<String> questions = (List<String>) request.get("questions");
            String sessionType = request.get("sessionType") instanceof String ? (String) request.get("sessionType") : "behavioral-interview";
            String practiceMode = request.get("practiceMode") instanceof String ? (String) request.get("practiceMode") : null;
            String audience = request.get("audience") instanceof String ? (String) request.get("audience") : null;
            String topic = request.get("topic") instanceof String ? (String) request.get("topic") : null;
            String promptContext = request.get("promptContext") instanceof String ? (String) request.get("promptContext") : null;
            @SuppressWarnings("unchecked")
            Map<String, Object> generatedPrompt = request.get("generatedPrompt") instanceof Map
                    ? (Map<String, Object>) request.get("generatedPrompt")
                    : null;
            
            System.out.println("Creating interview with title: " + title + ", role: " + role);
            Interview interview = interviewService.createInterview(
                    user,
                    title,
                    role,
                    languages,
                    difficulty,
                    questions,
                    sessionType,
                    practiceMode,
                    audience,
                    topic,
                    promptContext,
                    generatedPrompt
            );
            System.out.println("Interview created with ID: " + interview.getId());
            return ResponseEntity.ok(interview);
        } catch (Exception e) {
            System.err.println("Error creating interview: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/current_question_index")
    public ResponseEntity<?> updateInterviewCurrentQuestionIndex(@PathVariable Long id,
                                                                 @RequestBody Map<String, Integer> request,
                                                        HttpServletRequest httpRequest) {
        try {
            User user = getCurrentUser(httpRequest);
            Integer currentQuestionIndex = request.get("currentQuestionIndex");
            Interview interview = interviewService.updateCurrentQuestionIndex(id, user, currentQuestionIndex);
            return ResponseEntity.ok(interview);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PutMapping("/{id}/messages")
    public ResponseEntity<?> updateInterviewMessages(@PathVariable Long id, 
                                                  @RequestBody Map<String, Object> request, 
                                                  HttpServletRequest httpRequest) {
        try {
            User user = getCurrentUser(httpRequest);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> messages = (List<Map<String, Object>>) request.get("messages");
            
            Interview interview = interviewService.updateInterviewMessages(id, user, messages);
            return ResponseEntity.ok(interview);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateInterviewStatus(@PathVariable Long id, 
                                                @RequestBody Map<String, String> request, 
                                                HttpServletRequest httpRequest) {
        try {
            User user = getCurrentUser(httpRequest);
            String status = request.get("status");
            
            Interview interview = interviewService.updateInterviewStatus(id, user, status);
            return ResponseEntity.ok(interview);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/title")
    public ResponseEntity<?> updateInterviewTitle(@PathVariable Long id,
                                                  @RequestBody Map<String, String> request,
                                                  HttpServletRequest httpRequest) {
        try {
            User user = getCurrentUser(httpRequest);
            String title = request.get("title");
            Interview interview = interviewService.updateInterviewTitle(id, user, title);
            return ResponseEntity.ok(interview);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PutMapping("/{id}/grading")
    public ResponseEntity<?> updateGradingResult(@PathVariable Long id, 
                                               @RequestBody Map<String, Object> request, 
                                               HttpServletRequest httpRequest) {
        try {
            User user = getCurrentUser(httpRequest);
            @SuppressWarnings("unchecked")
            Map<String, Object> gradingResult = (Map<String, Object>) request.get("gradingResult");
            
            Interview interview = interviewService.updateGradingResult(id, user, gradingResult);
            return ResponseEntity.ok(interview);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/test")
    public ResponseEntity<?> testEndpoint() {
        return ResponseEntity.ok(Map.of("message", "Interview controller is working!"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteInterview(@PathVariable Long id, HttpServletRequest request) {
        try {
            User user = getCurrentUser(request);
            interviewService.deleteInterview(id, user);
            return ResponseEntity.ok(Map.of("message", "Interview deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    private User getCurrentUser(HttpServletRequest request) {
        String token = null;
        System.out.println("Checking for authentication token...");
        if (request.getCookies() != null) {
            System.out.println("Found " + request.getCookies().length + " cookies");
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                System.out.println("Cookie: " + cookie.getName() + " = " + cookie.getValue());
                if ("token".equals(cookie.getName())) {
                    token = cookie.getValue();
                    System.out.println("Found token: " + token.substring(0, Math.min(20, token.length())) + "...");
                }
            }
        } else {
            System.out.println("No cookies found");
        }
        
        if (token == null) {
            // For testing purposes, create a default user
            System.out.println("No token found, using default user for testing");
            return userRepository.findByEmail("test@example.com")
                    .orElseGet(() -> {
                        // Create a default user if it doesn't exist
                        System.out.println("Creating default user...");
                        User defaultUser = User.builder()
                                .firstname("Test")
                                .lastname("User")
                                .email("test@example.com")
                                .username("testuser")
                                .password("password")
                                .role(Role.USER)
                                .build();
                        User savedUser = userRepository.save(defaultUser);
                        System.out.println("Created default user with ID: " + savedUser.getId());
                        return savedUser;
                    });
        }
        
        String email = jwtService.extractUsername(token);
        System.out.println("Extracted email from token: " + email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!jwtService.isTokenValid(token, user)) {
            throw new RuntimeException("Invalid or expired token");
        }

        return user;
    }
} 
