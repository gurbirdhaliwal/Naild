package com.knappi.security.interview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knappi.security.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InterviewService {
    
    private final InterviewRepository interviewRepository;
    private final ObjectMapper objectMapper;
    
    public List<Interview> getUserInterviews(User user) {
        return interviewRepository.findActiveAndCompletedByUser(user);
    }
    
    public Optional<Interview> getInterviewById(Long id, User user) {
        return interviewRepository.findByIdAndUser(id, user);
    }
    
    public Interview saveInterview(Interview interview) {
        return interviewRepository.save(interview);
    }
    
    public void deleteInterview(Long id, User user) {
        Optional<Interview> interview = interviewRepository.findByIdAndUser(id, user);
        interview.ifPresent(interviewRepository::delete);
    }
    
    public Interview createInterview(User user, String title, String role, List<String> languages, 
                                  String difficulty, List<String> questions) {
        return createInterview(user, title, role, languages, difficulty, questions, "behavioral-interview", null, null, null, null, null);
    }

    public Interview createInterview(
            User user,
            String title,
            String role,
            List<String> languages,
            String difficulty,
            List<String> questions,
            String sessionType,
            String practiceMode,
            String audience,
            String topic,
            String promptContext,
            Map<String, Object> generatedPrompt
    ) {
        try {
            List<String> safeLanguages = languages == null ? List.of() : languages;
            List<String> safeQuestions = questions == null ? List.of() : questions;
            String resolvedRole = normalizeRequiredText(role, "Practice Session");
            String resolvedDifficulty = normalizeRequiredText(difficulty, "mid");
            String resolvedSessionType = normalizeRequiredText(sessionType, "behavioral-interview");
            String resolvedTitle = buildTitle(title, generatedPrompt, resolvedRole, practiceMode, topic, resolvedSessionType);

            Interview interview = Interview.builder()
                    .user(user)
                    .title(resolvedTitle)
                    .role(resolvedRole)
                    .languages(objectMapper.writeValueAsString(safeLanguages))
                    .difficulty(resolvedDifficulty)
                    .sessionType(resolvedSessionType)
                    .practiceMode(normalizeOptionalText(practiceMode))
                    .audience(normalizeOptionalText(audience))
                    .topic(normalizeOptionalText(topic))
                    .promptContext(normalizeOptionalText(promptContext))
                    .generatedPrompt(generatedPrompt == null ? null : objectMapper.writeValueAsString(generatedPrompt))
                    .status("active")
                    .questions(objectMapper.writeValueAsString(safeQuestions))
                    .messages("[]")
                    .currentQuestionIndex(0)
                    .build();
            
            return interviewRepository.save(interview);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error creating interview", e);
        }
    }

    public Interview updateCurrentQuestionIndex(Long interviewId, User user, Integer currentQuestionIndex) {
        Optional<Interview> interviewOpt = interviewRepository.findByIdAndUser(interviewId, user);
        if (interviewOpt.isEmpty()) {
            throw new RuntimeException("Interview not found");
        }

        Interview interview = interviewOpt.get();
        interview.setCurrentQuestionIndex(currentQuestionIndex);
        return interviewRepository.save(interview);
    }

    public Interview updateInterviewMessages(Long interviewId, User user, List<Map<String, Object>> messages) {
        Optional<Interview> interviewOpt = interviewRepository.findByIdAndUser(interviewId, user);
        if (interviewOpt.isEmpty()) {
            throw new RuntimeException("Interview not found");
        }
        
        Interview interview = interviewOpt.get();
        try {
            interview.setMessages(objectMapper.writeValueAsString(messages));
            return interviewRepository.save(interview);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error updating interview messages", e);
        }
    }
    
    public Interview updateInterviewStatus(Long interviewId, User user, String status) {
        Optional<Interview> interviewOpt = interviewRepository.findByIdAndUser(interviewId, user);
        if (interviewOpt.isEmpty()) {
            throw new RuntimeException("Interview not found");
        }
        
        Interview interview = interviewOpt.get();
        interview.setStatus(status);
        return interviewRepository.save(interview);
    }

    public Interview updateInterviewTitle(Long interviewId, User user, String title) {
        Optional<Interview> interviewOpt = interviewRepository.findByIdAndUser(interviewId, user);
        if (interviewOpt.isEmpty()) {
            throw new RuntimeException("Interview not found");
        }

        Interview interview = interviewOpt.get();
        interview.setTitle(title);
        return interviewRepository.save(interview);
    }
    
    public Interview updateGradingResult(Long interviewId, User user, Map<String, Object> gradingResult) {
        Optional<Interview> interviewOpt = interviewRepository.findByIdAndUser(interviewId, user);
        if (interviewOpt.isEmpty()) {
            throw new RuntimeException("Interview not found");
        }
        
        Interview interview = interviewOpt.get();
        try {
            interview.setGradingResult(objectMapper.writeValueAsString(gradingResult));
            interview.setStatus("completed");
            return interviewRepository.save(interview);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error updating grading result", e);
        }
    }
    
    public List<String> getLanguagesFromInterview(Interview interview) {
        try {
            return objectMapper.readValue(interview.getLanguages(), new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
    
    public List<String> getQuestionsFromInterview(Interview interview) {
        try {
            return objectMapper.readValue(interview.getQuestions(), new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
    
    public List<Map<String, Object>> getMessagesFromInterview(Interview interview) {
        try {
            return objectMapper.readValue(interview.getMessages(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
    
    public Map<String, Object> getGradingResultFromInterview(Interview interview) {
        if (interview.getGradingResult() == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(interview.getGradingResult(), new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private String normalizeRequiredText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String buildTitle(
            String title,
            Map<String, Object> generatedPrompt,
            String role,
            String practiceMode,
            String topic,
            String sessionType
    ) {
        String explicitTitle = normalizeOptionalText(title);
        if (explicitTitle != null) {
            return explicitTitle;
        }

        if (generatedPrompt != null) {
            Object generatedTitle = generatedPrompt.get("title");
            if (generatedTitle instanceof String generatedTitleText) {
                String normalizedGeneratedTitle = normalizeOptionalText(generatedTitleText);
                if (normalizedGeneratedTitle != null) {
                    return normalizedGeneratedTitle;
                }
            }
        }

        String normalizedTopic = normalizeOptionalText(topic);
        if ("tech-chat".equals(normalizeRequiredText(sessionType, "behavioral-interview"))) {
            return normalizedTopic != null ? "Tech Chat • " + normalizedTopic : role + " • Tech Chat";
        }

        if ("technical-communication".equals(normalizeRequiredText(sessionType, "behavioral-interview"))) {
            if (normalizedTopic != null) {
                return role + " • " + normalizedTopic;
            }

            String normalizedPracticeMode = normalizeOptionalText(practiceMode);
            if (normalizedPracticeMode != null) {
                return role + " • " + normalizedPracticeMode.replace('-', ' ');
            }

            return role + " • Technical communication";
        }

        return role + " Behavioral Interview";
    }
}
