package com.Project.ExpenseTracker.service;

import com.Project.ExpenseTracker.Model.User;

import com.Project.ExpenseTracker.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public User registerUser(User user) {
        if (userRepo.findByUsername(user.getUsername()) != null) {
            throw new RuntimeException("Username already exists");
        }
        // hash password before saving
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepo.save(user);
    }

    //Login
    public User loginUser(String username,String password){
        User existingUser = userRepo.findByUsername(username);
        if(existingUser!=null && passwordEncoder.matches(password, existingUser.getPassword())){
            return existingUser;
        }
         return null;
    }

}
