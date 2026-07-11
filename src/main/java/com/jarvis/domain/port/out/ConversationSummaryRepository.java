package com.jarvis.domain.port.out;

import com.jarvis.domain.model.ConversationSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationSummaryRepository extends JpaRepository<ConversationSummary, Long> {

    Optional<ConversationSummary> findByConversationId(Long conversationId);

    @Query("SELECT s FROM ConversationSummary s WHERE " +
           "LOWER(s.summary) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(s.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(s.keywords) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "ORDER BY s.conversationEndedAt DESC")
    List<ConversationSummary> searchByKeyword(@Param("q") String query);

    @Query("SELECT s FROM ConversationSummary s ORDER BY s.conversationEndedAt DESC")
    List<ConversationSummary> findAllRecent();
}
