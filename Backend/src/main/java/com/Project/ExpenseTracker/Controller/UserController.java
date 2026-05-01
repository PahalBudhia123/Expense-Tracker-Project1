package com.Project.ExpenseTracker.Controller;

import com.Project.ExpenseTracker.Model.User;
import com.Project.ExpenseTracker.service.UserService;
import com.Project.ExpenseTracker.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins= "http://127.0.0.1:5500")
public class UserController {

    @Autowired
    private UserService userservice;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        User saved = userservice.registerUser(user);
        if(saved!=null){
            String token = JwtUtil.generateToken(saved.getId(), saved.getUsername());
            saved.setPassword(null);
            return ResponseEntity.ok().header("Authorization", "Bearer " + token).body(saved);
        }
        else{
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }

    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User user) {
        User loggedUser = userservice.loginUser(user.getUsername(), user.getPassword());
        if (loggedUser != null) {
            String token = JwtUtil.generateToken(loggedUser.getId(), loggedUser.getUsername());
            loggedUser.setPassword(null);
            return ResponseEntity.ok().header("Authorization", "Bearer " + token).body(loggedUser);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }
    }
}

