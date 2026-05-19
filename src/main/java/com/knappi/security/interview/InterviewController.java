package com.knappi.security.interview;

import com.knappi.security.config.JwtService;
import com.knappi.security.user.User;
import com.knappi.security.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/interviews")
@RequiredArgsConstructor
public class InterviewController {
    
    private final InterviewService interviewService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    
    @GetMapping
    public ResponseEntity<?> getUserInterviews(Authentication authentication, HttpServletRequest request) {
        try {
            User user = getCurrentUser(authentication, request);
            List<Interview> interviews = interviewService.getUserInterviews(user);
            return ResponseEntity.ok(interviews);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<?> getInterviewById(@PathVariable Long id, Authentication authentication, HttpServletRequest request) {
        try {
            User user = getCurrentUser(authentication, request);
            Optional<Interview> interview = interviewService.getInterviewById(id, user);
            if (interview.isPresent()) {
                return ResponseEntity.ok(interview.get());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping
    public ResponseEntity<?> createInterview(@RequestBody Map<String, Object> requestBody, Authentication authentication, HttpServletRequest request) {
        try {
            User user = getCurrentUser(authentication, request);
            
            String title = (String) requestBody.get("title");
            String role = (String) requestBody.get("role");
            @SuppressWarnings("unchecked")
            List<String> languages = (List<String>) requestBody.get("languages");
            String difficulty = (String) requestBody.get("difficulty");
            @SuppressWarnings("unchecked")
            List<String> questions = (List<String>) requestBody.get("questions");
            String sessionType = requestBody.get("sessionType") instanceof String ? (String) requestBody.get("sessionType") : "behavioral-interview";
            String practiceMode = requestBody.get("practiceMode") instanceof String ? (String) requestBody.get("practiceMode") : null;
            String audience = requestBody.get("audience") instanceof String ? (String) requestBody.get("audience") : null;
            String topic = requestBody.get("topic") instanceof String ? (String) requestBody.get("topic") : null;
            String promptContext = requestBody.get("promptContext") instanceof String ? (String) requestBody.get("promptContext") : null;
            @SuppressWarnings("unchecked")
            Map<String, Object> generatedPrompt = requestBody.get("generatedPrompt") instanceof Map
                    ? (Map<String, Object>) requestBody.get("generatedPrompt")
                    : null;
            
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
            return ResponseEntity.ok(interview);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/current_question_index")
    public ResponseEntity<?> updateInterviewCurrentQuestionIndex(@PathVariable Long id,
                                                                 @RequestBody Map<String, Integer> requestBody,
                                                                 Authentication authentication,
                                                                 HttpServletRequest request) {
        try {
            User user = getCurrentUser(authentication, request);
            Integer currentQuestionIndex = requestBody.get("currentQuestionIndex");
            Interview interview = interviewService.updateCurrentQuestionIndex(id, user, currentQuestionIndex);
            return ResponseEntity.ok(interview);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PutMapping("/{id}/messages")
    public ResponseEntity<?> updateInterviewMessages(@PathVariable Long id, 
                                                  @RequestBody Map<String, Object> requestBody, 
                                                  Authentication authentication,
                                                  HttpServletRequest request) {
        try {
            User user = getCurrentUser(authentication, request);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> messages = (List<Map<String, Object>>) requestBody.get("messages");
            
            Interview interview = interviewService.updateInterviewMessages(id, user, messages);
            return ResponseEntity.ok(interview);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateInterviewStatus(@PathVariable Long id, 
                                                @RequestBody Map<String, String> requestBody, 
                                                Authentication authentication,
                                                HttpServletRequest request) {
        try {
            User user = getCurrentUser(authentication, request);
            String status = requestBody.get("status");
            
            Interview interview = interviewService.updateInterviewStatus(id, user, status);
            return ResponseEntity.ok(interview);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/title")
    public ResponseEntity<?> updateInterviewTitle(@PathVariable Long id,
                                                  @RequestBody Map<String, String> requestBody,
                                                  Authentication authentication,
                                                  HttpServletRequest request) {
        try {
            User user = getCurrentUser(authentication, request);
            String title = requestBody.get("title");
            Interview interview = interviewService.updateInterviewTitle(id, user, title);
            return ResponseEntity.ok(interview);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PutMapping("/{id}/grading")
    public ResponseEntity<?> updateGradingResult(@PathVariable Long id, 
                                               @RequestBody Map<String, Object> requestBody, 
                                               Authentication authentication,
                                               HttpServletRequest request) {
        try {
            User user = getCurrentUser(authentication, request);
            @SuppressWarnings("unchecked")
            Map<String, Object> gradingResult = (Map<String, Object>) requestBody.get("gradingResult");
            
            Interview interview = interviewService.updateGradingResult(id, user, gradingResult);
            return ResponseEntity.ok(interview);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/test")
    public ResponseEntity<?> testEndpoint() {
        return ResponseEntity.ok(Map.of("message", "Interview controller is working!"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteInterview(@PathVariable Long id, Authentication authentication, HttpServletRequest request) {
        try {
            User user = getCurrentUser(authentication, request);
            interviewService.deleteInterview(id, user);
            return ResponseEntity.ok(Map.of("message", "Interview deleted successfully"));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    private User getCurrentUser(Authentication authentication, HttpServletRequest request) {
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof User user) {
                return user;
            }
        }

        String token = extractToken(request);
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        try {
            String email = jwtService.extractUsername(token);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));

            if (!jwtService.isTokenValid(token, user)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
            }

            return user;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }

        if (request.getCookies() == null) {
            return null;
        }

        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if ("token".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
} 
