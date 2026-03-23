package com.naild.security.interview;

import com.naild.security.user.User;
import lombok.RequiredArgsConstructor;
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
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"}, allowCredentials = "true")
public class InterviewController {
    
    private final InterviewService interviewService;
    
    @GetMapping
    public ResponseEntity<?> getUserInterviews(Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            List<Interview> interviews = interviewService.getUserInterviews(user);
            return ResponseEntity.ok(interviews);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<?> getInterviewById(@PathVariable Long id, Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
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
    public ResponseEntity<?> createInterview(@RequestBody Map<String, Object> request, Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            
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
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/current_question_index")
    public ResponseEntity<?> updateInterviewCurrentQuestionIndex(@PathVariable Long id,
                                                                 @RequestBody Map<String, Integer> request,
                                                                 Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
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
                                                  Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
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
                                                Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
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
                                                  Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
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
                                               Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
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
    public ResponseEntity<?> deleteInterview(@PathVariable Long id, Authentication authentication) {
        try {
            User user = getCurrentUser(authentication);
            interviewService.deleteInterview(id, user);
            return ResponseEntity.ok(Map.of("message", "Interview deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return user;
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
} 
