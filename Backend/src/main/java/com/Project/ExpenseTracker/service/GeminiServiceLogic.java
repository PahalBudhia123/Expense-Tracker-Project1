package com.Project.ExpenseTracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Service
public class GeminiServiceLogic {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    
    // Rate limiting variables - Gemini has generous free limits
    private static final long RATE_LIMIT_DELAY_MS = 200; // 200ms between requests
    private static final int MAX_CONCURRENT_REQUESTS = 3;
    private static final int MAX_RETRIES = 2;
    private long lastRequestTime = 0;
    private final Semaphore requestSemaphore = new Semaphore(MAX_CONCURRENT_REQUESTS);

    // Define your categories
    private static final List<String> EXPENSE_CATEGORIES = Arrays.asList(
            "Food & Dining", "Transportation", "Shopping", "Entertainment",
            "Bills & Utilities", "Healthcare", "Education", "Travel", "Other"
    );

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public GeminiServiceLogic(@Value("${gemini.api.key}") String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("❌ Gemini API key is missing! Please set gemini.api.key in application.properties");
        }
        
        this.apiKey = apiKey.trim();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(90))
                .build();
        this.objectMapper = new ObjectMapper();
        
        System.out.println("✅ Gemini Service initialized successfully");
    }

    // Enhanced local categorization - works without API calls
    private String localCategorize(String description) {
        String desc = description.toLowerCase().trim();
        
        // Food & Dining - Comprehensive patterns
        if (desc.matches(".*(food|restaurant|cafe|lunch|dinner|breakfast|snack|pizza|burger|kfc|mcdonald|domino|subway|starbucks|coffee|tea|meal|kitchen|cook|eat|drink|juice|soda|beer|wine|bar|pub|dining|feast|buffet|canteen|mess|zomato|swiggy|foodpanda|uber.?eats).*")) {
            return "Food & Dining";
        }
        
        // Transportation - All transport modes
        if (desc.matches(".*(uber|ola|taxi|cab|bus|train|metro|flight|plane|airport|petrol|diesel|fuel|gas|parking|toll|auto|rickshaw|bike|car|vehicle|transport|travel|trip|journey).*")) {
            return "Transportation";
        }
        
        // Bills & Utilities - All utility bills
        if (desc.matches(".*(bill|electricity|electric|power|water|internet|wifi|broadband|mobile|phone|recharge|gas|cylinder|rent|maintenance|society|apartment|flat|house|home|utility|jio|airtel|vodafone|idea|bsnl|tata|reliance).*")) {
            return "Bills & Utilities";
        }
        
        // Shopping - Everything shopping related
        if (desc.matches(".*(shop|store|mall|market|buy|purchase|cloth|dress|shirt|pant|shoe|amazon|flipkart|myntra|grocery|vegetable|fruit|super|hypermarket|bazaar|fair|sale|big.?bazaar|dmart|more|spencer).*")) {
            return "Shopping";
        }
        
        // Entertainment - All entertainment
        if (desc.matches(".*(movie|cinema|theater|netflix|prime|hotstar|youtube|spotify|music|game|gaming|concert|show|party|club|disco|entertainment|fun|hobby|sport|gym|fitness).*")) {
            return "Entertainment";
        }
        
        // Healthcare - Medical expenses
        if (desc.matches(".*(medicine|medical|doctor|hospital|clinic|pharmacy|chemist|health|dental|eye|surgery|treatment|checkup|test|lab|ambulance|insurance|vaccine).*")) {
            return "Healthcare";
        }
        
        // Education - Learning related
        if (desc.matches(".*(education|school|college|university|course|class|tuition|book|notebook|pen|pencil|study|exam|fee|admission|library|online|udemy|coursera|learning).*")) {
            return "Education";
        }
        
        // Travel - Tourism and travel
        if (desc.matches(".*(travel|tour|holiday|vacation|hotel|resort|booking|airbnb|sightseeing|tourist|visit|trip|journey|adventure|explore).*")) {
            return "Travel";
        }
        
        return null; // No pattern matched
    }

    // Helper method to enforce rate limiting
    private void enforceRateLimit() throws InterruptedException {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastRequestTime;
        
        if (timeSinceLastRequest < RATE_LIMIT_DELAY_MS) {
            long waitTime = RATE_LIMIT_DELAY_MS - timeSinceLastRequest;
            Thread.sleep(waitTime);
        }
        
        lastRequestTime = System.currentTimeMillis();
    }

    // Call Gemini API with retry logic
    private String callGeminiWithRetry(String prompt) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                requestSemaphore.acquire();
                
                try {
                    enforceRateLimit();
                    return callGeminiAPI(prompt);
                } finally {
                    requestSemaphore.release();
                }
                
            } catch (Exception e) {
                lastException = e;
                String errorMsg = e.getMessage();
                
                // Check if it's a rate limit error
                if (errorMsg != null && (errorMsg.contains("429") || errorMsg.toLowerCase().contains("rate limit"))) {
                    if (attempt < MAX_RETRIES) {
                        long backoffDelay = (long) Math.pow(2, attempt) * 1000;
                        System.err.println("⚠️ Gemini rate limit hit (attempt " + attempt + "/" + MAX_RETRIES + "). Retrying in " + backoffDelay + "ms...");
                        Thread.sleep(backoffDelay);
                        continue;
                    }
                }
                throw e;
            }
        }
        
        throw lastException;
    }

    // Direct API call to Gemini
    private String callGeminiAPI(String prompt) throws IOException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;
        
        // Create request body
        String requestBody = String.format("""
            {
                "contents": [{
                    "parts": [{
                        "text": "%s"
                    }]
                }],
                "generationConfig": {
                    "temperature": 0.1,
                    "maxOutputTokens": 20,
                    "topP": 0.8,
                    "topK": 10
                }
            }""", prompt.replace("\"", "\\\""));

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

    public String categorizeExpense(String description) {
        // Check cache first
        String cacheKey = description.trim().toLowerCase();
        if (cache.containsKey(cacheKey)) {
            System.out.println("📋 Using cached result for: " + description);
            return cache.get(cacheKey);
        }

        // Apply local rules first (90% of cases will be handled here)
        String local = localCategorize(description);
        if (local != null) {
            System.out.println("🎯 Local categorization: " + description + " → " + local);
            cache.put(cacheKey, local);
            return local;
        }
        
        // Only use Gemini API for edge cases
        System.out.println("🤖 Using Gemini for categorization: " + description);
        
        String prompt = String.format(
                "Categorize this expense into exactly one category: %s\n\n" +
                "Expense: '%s'\n\n" +
                "Return only the category name, nothing else. If unsure, return 'Other'.",
                String.join(", ", EXPENSE_CATEGORIES),
                description
        );

        try {
            String category = callGeminiWithRetry(prompt);
            
            // Validate response
            for (String cat : EXPENSE_CATEGORIES) {
                if (cat.equalsIgnoreCase(category.trim()) || 
                    category.toLowerCase().contains(cat.toLowerCase().split(" ")[0])) {
                    cache.put(cacheKey, cat);
                    System.out.println("✅ Gemini categorization successful: " + description + " → " + cat);
                    return cat;
                }
            }
            
            // Invalid category returned
            cache.put(cacheKey, "Other");
            System.out.println("⚠️ Gemini returned invalid category '" + category + "', using 'Other'");
            return "Other";
            
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            System.err.println("❌ Gemini API error: " + errorMsg);
            
            cache.put(cacheKey, "Other");
            return "Other";
        }
    }

    public List<String> getAvailableCategories() {
        return EXPENSE_CATEGORIES;
    }
}
