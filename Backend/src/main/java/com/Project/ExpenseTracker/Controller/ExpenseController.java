package com.Project.ExpenseTracker.Controller;

import com.Project.ExpenseTracker.Model.Expense;
import com.Project.ExpenseTracker.Model.User;
import com.Project.ExpenseTracker.Repository.ExpenseTrackerRepository;
import com.Project.ExpenseTracker.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/expenses")
@CrossOrigin(origins = "http://127.0.0.1:5500")
public class ExpenseController {

    @Autowired
    private final ExpenseTrackerRepository ExpRepoObj;
    @Autowired
    private  UserRepository userRepoObj;

    public  ExpenseController(ExpenseTrackerRepository ExpRepoObj){

        this.ExpRepoObj = ExpRepoObj;
    }

    // Now APIS
    @GetMapping("/me")
    public ResponseEntity<List<Expense>> getMyExpenses(Authentication authentication){
        Long userId = Long.parseLong(((com.Project.ExpenseTracker.security.JwtUser) authentication.getPrincipal()).getId());
        List<Expense> list = ExpRepoObj.findByUserId(userId);
        return ResponseEntity.ok(list);
    }

    @PostMapping
    public ResponseEntity<Expense> CreateExpense(@RequestBody Expense expense, Authentication authentication){
        Long userId = Long.parseLong(((com.Project.ExpenseTracker.security.JwtUser) authentication.getPrincipal()).getId());
        Optional<User> userOpt = userRepoObj.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // force expense owner to be the authenticated user
        expense.setUser(userOpt.get());
        Expense savedExpense = ExpRepoObj.save(expense);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedExpense);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpenses(@PathVariable Long id, Authentication authentication){
        Long userId = Long.parseLong(((com.Project.ExpenseTracker.security.JwtUser) authentication.getPrincipal()).getId());
        Optional<Expense> found = ExpRepoObj.findById(id);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!found.get().getUser().getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ExpRepoObj.deleteById(id);
        return ResponseEntity.noContent().build();
    }
    @PutMapping("/{id}")
    public ResponseEntity<Expense> UpdateExpense(@PathVariable Long id , @RequestBody Expense NewExpense, Authentication authentication){
       Long userId = Long.parseLong(((com.Project.ExpenseTracker.security.JwtUser) authentication.getPrincipal()).getId());
       Optional<Expense> reqExpense = ExpRepoObj.findById(id);
       if(reqExpense.isEmpty()){
           return ResponseEntity.notFound().build();
       }
       Expense expense = reqExpense.get();
       if (!expense.getUser().getId().equals(userId)) {
           return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
       }
       if (NewExpense.getName() != null) expense.setName(NewExpense.getName());
       if (NewExpense.getDate() != null) expense.setDate(NewExpense.getDate());
       if (NewExpense.getAmount() != null) expense.setAmount(NewExpense.getAmount());
       if (NewExpense.getCategory() != null) expense.setCategory(NewExpense.getCategory());
       Expense saved = ExpRepoObj.save(expense);
       return ResponseEntity.ok(saved);
   }

}
