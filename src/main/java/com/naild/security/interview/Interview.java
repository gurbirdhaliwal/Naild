package com.naild.security.interview;

import com.naild.security.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "interviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Interview {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false)
    private String title;
    
    @Column(nullable = false)
    private String role;
    
    @Column(columnDefinition = "JSON")
    private String languages; // JSON array as string
    
    @Column(nullable = false)
    private String difficulty;
    
    @Column(nullable = false)
    private String status; // "active", "completed", "paused"
    
    @Column(columnDefinition = "JSON")
    private String questions; // JSON array as string
    
    @Column(columnDefinition = "JSON")
    private String messages; // JSON array as string
    
    private Integer currentQuestionIndex;
    
    @Column(columnDefinition = "JSON")
    private String gradingResult; // JSON object as string
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
} 