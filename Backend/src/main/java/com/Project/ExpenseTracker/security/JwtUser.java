package com.Project.ExpenseTracker.security;


// JWT token se nikal ke milne wale user ka minimal info hold karta hai. yani claims ko 1 class meh store kr rhe hai



public class JwtUser {
    private final String id;
    private final String username;

    public JwtUser(String id, String username) {
        this.id = id;
        this.username = username;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }
}


