package com.jarvis.domain.port.in;

import com.jarvis.domain.model.ConversationSummary;

import java.util.List;

public interface ConversationSummaryUseCase {

    ConversationSummary summarize(Long conversationId);

    List<ConversationSummary> search(String query);

    List<ConversationSummary> getRecent(int limit);
}
