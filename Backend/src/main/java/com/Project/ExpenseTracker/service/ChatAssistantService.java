/*package com.Project.ExpenseTracker.service;

import com.theokanning.openai.OpenAiService;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChatAssistantService {

    private final OpenAiService openAiService;
    private final List<ChatMessage> conversationHistory = new ArrayList<>();
    
    // Rate limiting for chat service - increased for strict OpenAI limits
    private static final long CHAT_RATE_LIMIT_MS = 10000; // 10 seconds between chat requests
    private static final int MAX_RETRIES = 2; // Reduced retries to avoid hitting limits
    private long lastChatRequestTime = 0;

    public ChatAssistantService(@Value("${openai.api.key}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("❌ OpenAI API key is missing! Please set in application.properties");
        }

        this.openAiService = new OpenAiService(apiKey.trim(), Duration.ofSeconds(90));
        System.out.println("✅ Chat Assistant Service initialized successfully");

        // Initial system role
        conversationHistory.add(new ChatMessage("system",
                "You are a personal finance assistant inside an Expense Tracker app. " +
                        "You analyze user's spending, answer their queries about past spending, " +
                        "and give useful budgeting tips. Keep answers simple and friendly."));
    }
    
    // Helper method to enforce rate limiting for chat
    private void enforceChatRateLimit() throws InterruptedException {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastChatRequestTime;
        
        if (timeSinceLastRequest < CHAT_RATE_LIMIT_MS) {
            long waitTime = CHAT_RATE_LIMIT_MS - timeSinceLastRequest;
            System.out.println("💬 Chat rate limiting: waiting " + waitTime + "ms...");
            Thread.sleep(waitTime);
        }
        
        lastChatRequestTime = System.currentTimeMillis();
    }

    // Limit history → avoid token overload
    private List<ChatMessage> getRecentHistory() {
        int keep = 6; // system + last 5 messages
        if (conversationHistory.size() <= keep) {
            return new ArrayList<>(conversationHistory);
        }

        List<ChatMessage> recent = new ArrayList<>();
        recent.add(conversationHistory.get(0)); // system
        recent.addAll(conversationHistory.subList(conversationHistory.size() - (keep - 1), conversationHistory.size()));
        return recent;
    }

    // Helper method for chat with retry logic
    private String callChatWithRetry(ChatCompletionRequest request) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                enforceChatRateLimit();
                
                ChatCompletionResult result = openAiService.createChatCompletion(request);
                return result.getChoices().get(0).getMessage().getContent();
                
            } catch (Exception e) {
                lastException = e;
                String errorMsg = e.getMessage();
                
                // Check if it's a rate limit error (HTTP 429)
                if (errorMsg != null && (errorMsg.contains("429") || errorMsg.toLowerCase().contains("rate limit"))) {
                    if (attempt < MAX_RETRIES) {
                        long backoffDelay = (long) Math.pow(2, attempt) * 1000; // Exponential backoff: 2s, 4s, 8s
                        System.err.println("⚠️ Chat rate limit hit (attempt " + attempt + "/" + MAX_RETRIES + "). Retrying in " + backoffDelay + "ms...");
                        Thread.sleep(backoffDelay);
                        continue;
                    } else {
                        System.err.println("❌ Chat rate limit exceeded after " + MAX_RETRIES + " attempts.");
                        throw e;
                    }
                } else {
                    // For non-rate-limit errors, don't retry
                    System.err.println("❌ Chat API error (attempt " + attempt + "): " + errorMsg);
                    throw e;
                }
            }
        }
        
        throw lastException;
    }

    // User query → AI response
    public String askAssistant(String userMessage) {
        conversationHistory.add(new ChatMessage("user", userMessage));
        System.out.println("💬 Processing chat message: " + userMessage.substring(0, Math.min(50, userMessage.length())) + "...");

        try {
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model("gpt-3.5-turbo")  // Use standard model for cost efficiency
                    .messages(getRecentHistory())
                    .maxTokens(200) // Reduced tokens for efficiency
                    .temperature(0.7)
                    .build();

            String answer = callChatWithRetry(request);
            
            conversationHistory.add(new ChatMessage("assistant", answer));
            System.out.println("✅ Chat response generated successfully");
            return answer;

        } catch (Exception e) {
            String msg = e.getMessage();
            System.err.println("❌ OpenAI Chat API error: " + msg);

            if (msg != null && (msg.contains("429") || msg.toLowerCase().contains("rate limit"))) {
                System.err.println("💡 Chat Suggestion: You may be hitting OpenAI rate limits. Consider upgrading your OpenAI plan.");
                return "⚠️ I'm receiving too many requests right now. Please wait a moment and try again. If this persists, you might need to upgrade your OpenAI plan.";
            } else if (msg != null && msg.toLowerCase().contains("quota")) {
                return "⚠️ Your OpenAI quota has been exceeded. Please check your OpenAI account billing.";
            }
            return "⚠️ Sorry, I'm temporarily unavailable. Please try again in a few moments.";
        }
    }
}
/*
    // Scheduled daily financial tip
    @Scheduled(cron = "0 10 9 * * ?") // every day at 9:10 AM
    public void dailySuggestion() {
        String prompt = "Give the user one short financial suggestion based on their spending habits. " +
                "Be specific like 'You are spending too much on Food this week, try reducing it by 10%'. " +
                "If no spending data is available, give a generic tip.";

        conversationHistory.add(new ChatMessage("user", prompt));

        try {
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model("gpt-3.5-turbo-0125")
                    .messages(getRecentHistory())
                    .maxTokens(150)
                    .temperature(0.6)
                    .build();

            ChatCompletionResult result = openAiService.createChatCompletion(request);
            String suggestion = result.getChoices().get(0).getMessage().getContent();

            System.out.println("💡 Daily Suggestion: " + suggestion);

        } catch (Exception e) {
            System.err.println("⚠️ Daily suggestion failed: " + e.getMessage());
        }
    }
}*/




