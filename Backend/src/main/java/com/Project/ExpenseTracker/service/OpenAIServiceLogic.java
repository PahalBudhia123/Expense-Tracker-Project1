/*package com.Project.ExpenseTracker.service;

import com.openai.OpenAI;
import com.openai.api.chat.ChatCompletionCreateParams;
import com.openai.api.chat.ChatCompletionMessage;
import com.openai.api.chat.ChatCompletion;
import com.openai.api.chat.ChatCompletionChoice;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class OpenAiServiceLogic {

    private final OpenAI openAI;

    // Define your categories
    private static final List<String> EXPENSE_CATEGORIES = Arrays.asList(
            "Food & Dining", "Transportation", "Shopping", "Entertainment",
            "Bills & Utilities", "Healthcare", "Education", "Travel", "Other"
    );

    public OpenAiServiceLogic(@Value("${openai.api.key}") String apiKey) {
        this.openAI = new OpenAI(apiKey);
    }

    public String categorizeExpense(String description) {
        String prompt = String.format(
                "Categorize this expense description: '%s'. " +
                        "Available categories: %s. " +
                        "Return ONLY the category name, nothing else. " +
                        "If unsure, return 'Other'.",
                description,
                String.join(", ", EXPENSE_CATEGORIES)
        );

        ChatCompletionCreateParams request = ChatCompletionCreateParams.builder()
                .model("gpt-4o") // Use "gpt-4o" or any latest model your API key supports
                .messages(List.of(
                        ChatCompletionMessage.builder()
                                .role("user")
                                .content(prompt)
                                .build()
                ))
                .maxTokens(10)
                .temperature(0.1)
                .build();

        try {
            ChatCompletion response = openAI.chat().completions().create(request);
            if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                ChatCompletionChoice choice = response.getChoices().get(0);
                String category = choice.getMessage().getContent().trim();
                // Validate the response is one of our categories
                if (EXPENSE_CATEGORIES.contains(category)) {
                    return category;
                }
            }
            return "Other";
        } catch (Exception e) {
            System.err.println("OpenAI API error: " + e.getMessage());
            return "Other";
        }
    }

    public List<String> getAvailableCategories() {
        return EXPENSE_CATEGORIES;
    }
}*/
/*
package com.Project.ExpenseTracker.service;



import com.theokanning.openai.OpenAiService;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;


@Service
public class OpenAIServiceLogic {

    private final OpenAiService openAiService;
    
    // Rate limiting variables - increased for strict OpenAI limits
    private static final long RATE_LIMIT_DELAY_MS = 5000; // 5 seconds between requests
    private static final int MAX_CONCURRENT_REQUESTS = 1; // Max 1 simultaneous request
    private static final int MAX_RETRIES = 2; // Reduced retries
    private long lastRequestTime = 0;
    private final Semaphore requestSemaphore = new Semaphore(MAX_CONCURRENT_REQUESTS);

    // Define your categories
    private static final List<String> EXPENSE_CATEGORIES = Arrays.asList(
            "Food & Dining", "Transportation", "Shopping", "Entertainment",
            "Bills & Utilities", "Healthcare", "Education", "Travel", "Other"
    );

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public OpenAIServiceLogic(@Value("${openai.api.key}") String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("❌ OpenAI API key is missing! Please set in application.properties");
        }
        // Initialize OpenAiService with 90 seconds timeout
        this.openAiService = new OpenAiService(apiKey.trim(), Duration.ofSeconds(90));
        System.out.println("✅ OpenAI Service initialized successfully");
    }

    private String localCategorize(String description) {
        String desc = description.toLowerCase();
        if (desc.contains("food") || desc.contains("restaurant") || desc.contains("cafe") || desc.contains("lunch") || desc.contains("dinner")) {
            return "Food & Dining";
        } else if (desc.contains("uber") || desc.contains("taxi") || desc.contains("bus") || desc.contains("train") || desc.contains("flight")) {
            return "Transportation";
        } else if (desc.contains("electricity") || desc.contains("water") || desc.contains("internet") || desc.contains("bill")) {
            return "Bills & Utilities";
        } else if (desc.contains("movie") || desc.contains("netflix") || desc.contains("concert") || desc.contains("game")) {
            return "Entertainment";
        } else if (desc.contains("medicine") || desc.contains("doctor") || desc.contains("hospital")) {
            return "Healthcare";
        } else if (desc.contains("book") || desc.contains("course") || desc.contains("tuition")) {
            return "Education";
        } else if (desc.contains("hotel") || desc.contains("flight") || desc.contains("travel") || desc.contains("trip")) {
            return "Travel";
        }
        return null; // Rule match nahi hua
    }
    private String Normalizes(String str) {
        if (str == null) return "";
        return str.toLowerCase().replaceAll("[^a-z]", "");
    }

    // Helper method to enforce rate limiting
    private void enforceRateLimit() throws InterruptedException {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastRequestTime;
        
        if (timeSinceLastRequest < RATE_LIMIT_DELAY_MS) {
            long waitTime = RATE_LIMIT_DELAY_MS - timeSinceLastRequest;
            System.out.println("⏳ Rate limiting: waiting " + waitTime + "ms...");
            Thread.sleep(waitTime);
        }
        
        lastRequestTime = System.currentTimeMillis();
    }
    
    // Helper method for exponential backoff retry
    private String callOpenAIWithRetry(ChatCompletionRequest chatRequest) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // Acquire semaphore to limit concurrent requests
                requestSemaphore.acquire();
                
                try {
                    enforceRateLimit();
                    
                    String category = openAiService.createChatCompletion(chatRequest)
                            .getChoices()
                            .get(0)
                            .getMessage()
                            .getContent()
                            .trim();
                    
                    return category;
                    
                } finally {
                    requestSemaphore.release();
                }
                
            } catch (Exception e) {
                lastException = e;
                String errorMsg = e.getMessage();
                
                // Check if it's a rate limit error (HTTP 429)
                if (errorMsg != null && (errorMsg.contains("429") || errorMsg.toLowerCase().contains("rate limit"))) {
                    if (attempt < MAX_RETRIES) {
                        long backoffDelay = (long) Math.pow(2, attempt) * 1000; // Exponential backoff: 2s, 4s, 8s
                        System.err.println("⚠️ Rate limit hit (attempt " + attempt + "/" + MAX_RETRIES + "). Retrying in " + backoffDelay + "ms...");
                        Thread.sleep(backoffDelay);
                        continue;
                    } else {
                        System.err.println("❌ Rate limit exceeded after " + MAX_RETRIES + " attempts. Giving up.");
                        throw e;
                    }
                } else {
                    // For non-rate-limit errors, don't retry
                    System.err.println("❌ OpenAI API error (attempt " + attempt + "): " + errorMsg);
                    throw e;
                }
            }
        }
        
        throw lastException;
    }

    public String categorizeExpense(String description) {
        // Check cache first
        String cacheKey = description.trim().toLowerCase();
        if (cache.containsKey(cacheKey)) {
            System.out.println("📋 Using cached result for: " + description);
            return cache.get(cacheKey);
        }

        // Apply local rules first (no API call needed)
        String local = localCategorize(description);
        if (local != null) {
            System.out.println("🎯 Local categorization: " + description + " -> " + local);
            cache.put(cacheKey, local);
            return local;
        }
        
        // Only use OpenAI API if local rules didn't match
        System.out.println("🤖 Using OpenAI for categorization: " + description);
        
        String prompt = String.format(
                "You are an assistant that categorizes expenses into one of these categories: %s. " +
                        "Here are examples: " +
                        "- 'McDonald's' -> 'Food & Dining'\n" +
                        "- 'Uber ride' -> 'Transportation'\n" +
                        "- 'electricity bill' -> 'Bills & Utilities'\n" +
                        "Now categorize this expense description: '%s'. " +
                        "Return exactly one of the category names, nothing else. " +
                        "If unsure, return 'Other'.",
                String.join(", ", EXPENSE_CATEGORIES),
                description
        );
        
        ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo") // Use most cost-effective model
                .messages(List.of(
                        new ChatMessage("system", "You are an expense categorization assistant."),
                        new ChatMessage("user", prompt)
                ))
                .maxTokens(20) // Reduced tokens for simple categorization
                .temperature(0.0) // Deterministic responses
                .build();

        try {
            String category = callOpenAIWithRetry(chatRequest);
            String normalizedAI = Normalizes(category);

            // Compare with valid categories
            for (String cat : EXPENSE_CATEGORIES) {
                if (Normalizes(cat).equals(normalizedAI)) {
                    cache.put(cacheKey, cat);
                    System.out.println("✅ AI categorization successful: " + description + " -> " + cat);
                    return cat;
                }
            }
            
            // If AI returned invalid category, use "Other"
            cache.put(cacheKey, "Other");
            System.out.println("⚠️ AI returned invalid category '" + category + "', using 'Other'");
            return "Other";
            
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            System.err.println("❌ OpenAI API error: " + errorMsg);
            
            // Cache the failure to avoid repeated API calls for the same description
            cache.put(cacheKey, "Other");
            
            if (errorMsg != null && (errorMsg.contains("429") || errorMsg.toLowerCase().contains("rate limit"))) {
                System.err.println("💡 Suggestion: You may be hitting OpenAI rate limits. Consider upgrading your OpenAI plan.");
            }
            
            return "Other";
        }
    }
    public List<String> getAvailableCategories() {
        return EXPENSE_CATEGORIES;
    }

            /*
            for (String cat : EXPENSE_CATEGORIES) {
                if (cat.equalsIgnoreCase(category) || category.toLowerCase().contains(cat.toLowerCase().split("&")[0].trim())) {
                    return cat;
                }
            }*/

/*
            // Agar AI ne galat answer diya to default "Other"
            if (EXPENSE_CATEGORIES.contains(category)) {
                return category;
            } else {
                return "Other";
            }


            String lowerDesc = description.toLowerCase();
            if (lowerDesc.contains("food") || lowerDesc.contains("restaurant") || lowerDesc.contains("cafe")) {
                return "Food & Dining";
            } else if (lowerDesc.contains("uber") || lowerDesc.contains("bus") || lowerDesc.contains("taxi")) {
                return "Transportation";
            } else if (lowerDesc.contains("electricity") || lowerDesc.contains("internet") || lowerDesc.contains("water")) {
                return "Bills & Utilities";
            }*/




