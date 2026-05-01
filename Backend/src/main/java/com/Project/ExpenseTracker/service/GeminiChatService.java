package com.Project.ExpenseTracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class GeminiChatService {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    
    // Rate limiting for chat service - Gemini is generous
    private static final long CHAT_RATE_LIMIT_MS = 500; // 500ms between chat requests
    private static final int MAX_RETRIES = 2;
    private long lastChatRequestTime = 0;
    
    // Simple conversation history
    private final List<String> conversationHistory = new ArrayList<>();

    public GeminiChatService(@Value("${gemini.api.key}") String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("❌ Gemini API key is missing! Please set gemini.api.key in application.properties");
        }

        this.apiKey = apiKey.trim();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(90))
                .build();
        this.objectMapper = new ObjectMapper();
        
        System.out.println("✅ Gemini Chat Assistant Service initialized successfully");

        // Initial system context
        conversationHistory.add("System: You are a personal finance assistant inside an Expense Tracker app. " +
                "You analyze user's spending, answer their queries about past spending, " +
                "and give useful budgeting tips. Keep answers simple and friendly.");
    }
    
    // Helper method to enforce rate limiting for chat
    private void enforceChatRateLimit() throws InterruptedException {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastChatRequestTime;
        
        if (timeSinceLastRequest < CHAT_RATE_LIMIT_MS) {
            long waitTime = CHAT_RATE_LIMIT_MS - timeSinceLastRequest;
            Thread.sleep(waitTime);
        }
        
        lastChatRequestTime = System.currentTimeMillis();
    }

    // Limit history to avoid token overload
    private String getRecentHistory() {
        int keep = 10; // Keep last 10 messages
        if (conversationHistory.size() <= keep) {
            return String.join("\n", conversationHistory);
        }
        
        List<String> recent = new ArrayList<>();
        recent.add(conversationHistory.get(0)); // System message
        recent.addAll(conversationHistory.subList(conversationHistory.size() - (keep - 1), conversationHistory.size()));
        return String.join("\n", recent);
    }

    // Direct API call to Gemini for chat
    private String callGeminiChatAPI(String prompt) throws IOException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;
        
        // Create request body with conversation context
        String fullPrompt = getRecentHistory() + "\nUser: " + prompt + "\nAssistant:";
        
        String requestBody = String.format("""
            {
                "contents": [{
                    "parts": [{
                        "text": "%s"
                    }]
                }],
                "generationConfig": {
                    "temperature": 0.7,
                    "maxOutputTokens": 200,
                    "topP": 0.9,
                    "topK": 20
                }
            }""", fullPrompt.replace("\"", "\\\""));

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Gemini API error: HTTP " + response.code() + " - " + response.message());
            }

            String responseBody = response.body().string();
            JsonNode jsonResponse = objectMapper.readTree(responseBody);
            
            // Parse Gemini response
            JsonNode candidates = jsonResponse.get("candidates");
            if (candidates != null && candidates.isArray() && candidates.size() > 0) {
                JsonNode content = candidates.get(0).get("content");
                if (content != null) {
                    JsonNode parts = content.get("parts");
                    if (parts != null && parts.isArray() && parts.size() > 0) {
                        JsonNode text = parts.get(0).get("text");
                        if (text != null) {
                            return text.asText().trim();
                        }
                    }
                }
            }
            
            throw new IOException("Invalid response format from Gemini API");
        }
    }

    // Helper method for chat with retry logic
    private String callChatWithRetry(String userMessage) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                enforceChatRateLimit();
                return callGeminiChatAPI(userMessage);
                
            } catch (Exception e) {
                lastException = e;
                String errorMsg = e.getMessage();
                
                // Check if it's a rate limit error
                if (errorMsg != null && (errorMsg.contains("429") || errorMsg.toLowerCase().contains("rate limit"))) {
                    if (attempt < MAX_RETRIES) {
                        long backoffDelay = (long) Math.pow(2, attempt) * 1000; // Exponential backoff
                        System.err.println("⚠️ Gemini chat rate limit hit (attempt " + attempt + "/" + MAX_RETRIES + "). Retrying in " + backoffDelay + "ms...");
                        Thread.sleep(backoffDelay);
                        continue;
                    } else {
                        System.err.println("❌ Gemini chat rate limit exceeded after " + MAX_RETRIES + " attempts.");
                        throw e;
                    }
                } else {
                    // For non-rate-limit errors, don't retry
                    System.err.println("❌ Gemini Chat API error (attempt " + attempt + "): " + errorMsg);
                    throw e;
                }
            }
        }
        
        throw lastException;
    }

    // User query → AI response
    public String askAssistant(String userMessage) {
        conversationHistory.add("User: " + userMessage);
        System.out.println("💬 Processing chat message: " + userMessage.substring(0, Math.min(50, userMessage.length())) + "...");
        System.out.println("🔗 Making API call to Gemini...");

        try {
            String answer = callChatWithRetry(userMessage);
            
            conversationHistory.add("Assistant: " + answer);
            System.out.println("✅ Gemini chat response generated successfully");
            return answer;

        } catch (Exception e) {
            String msg = e.getMessage();
            System.err.println("❌ Gemini Chat API error: " + msg);

            if (msg != null && (msg.contains("429") || msg.toLowerCase().contains("rate limit"))) {
                return "⚠️ I'm receiving too many requests right now. Please wait a moment and try again.";
            } else if (msg != null && msg.toLowerCase().contains("quota")) {
                return "⚠️ Service quota exceeded. Please try again later.";
            }
            return "⚠️ Sorry, I'm temporarily unavailable. Please try again in a few moments.";
        }
    }
}
