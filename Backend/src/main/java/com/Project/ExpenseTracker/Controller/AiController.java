package com.Project.ExpenseTracker.Controller;


import com.Project.ExpenseTracker.service.GeminiServiceLogic;
import com.Project.ExpenseTracker.service.GeminiChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "http://127.0.0.1:5500")
public class AiController {

    @Autowired
    private GeminiServiceLogic geminiService;

    @Autowired
    private GeminiChatService geminiChatService;

    /**
     * Get AI-suggested category for an expense description
     * @param request JSON with "description" field
     * @return Suggested category
     */
    @PostMapping("/categorize")
    public ResponseEntity<Map<String, String>> categorizeExpense(@RequestBody Map<String, String> request) {
        try {
            String description = request.get("description");
            if (description == null || description.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Description is required"));
            }

            String suggestedCategory = geminiService.categorizeExpense(description.trim());
            
            return ResponseEntity.ok(Map.of(
                "suggestedCategory", suggestedCategory,
                "description", description
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to categorize expense: " + e.getMessage()));
        }
    }

    /**
     * Get all available expense categories
     * @return List of available categories
     */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getAvailableCategories() {
        try {
            List<String> categories = geminiService.getAvailableCategories();
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(List.of("Other")); // Fallback
        }
    }

    @PostMapping("/ask")
    public ResponseEntity<Map<String, String>> askAssistant(@RequestBody Map<String, String> request) {
        try {
            String userMessage = request.get("prompt");
            if (userMessage == null || userMessage.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Prompt is required"));
            }
            
            String reply = geminiChatService.askAssistant(userMessage.trim());
            Map<String, String> response = new HashMap<>();
            response.put("reply", reply);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get AI response: " + e.getMessage()));
        }
    }
}
