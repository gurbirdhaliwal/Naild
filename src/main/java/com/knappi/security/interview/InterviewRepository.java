package com.knappi.security.interview;

import com.knappi.security.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface InterviewRepository extends JpaRepository<Interview, Long> {
    
    List<Interview> findByUserOrderByUpdatedAtDesc(User user);
    
    List<Interview> findByUserAndStatusOrderByUpdatedAtDesc(User user, String status);
    
    @Query("SELECT i FROM Interview i WHERE i.user = :user AND i.status IN ('active', 'completed') ORDER BY i.updatedAt DESC")
    List<Interview> findActiveAndCompletedByUser(@Param("user") User user);
    
    Optional<Interview> findByIdAndUser(Long id, User user);
    
    @Query("SELECT COUNT(i) FROM Interview i WHERE i.user = :user AND i.status = 'active'")
    long countActiveInterviewsByUser(@Param("user") User user);

    @Query("SELECT COUNT(i) FROM Interview i WHERE i.user = :user AND i.sessionType = :sessionType AND i.status = 'active' AND i.createdAt >= :createdAfter")
    long countActiveSessionsByUserAndTypeSince(
            @Param("user") User user,
            @Param("sessionType") String sessionType,
            @Param("createdAfter") LocalDateTime createdAfter
    );
} 
