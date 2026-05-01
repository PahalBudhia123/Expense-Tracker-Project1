package com.Project.ExpenseTracker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;

public class JwtUtil {
    private static final long EXPIRATION_MS = 1000L * 60 * 60 * 24; // 24h
    private static final Key KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);

    public static String generateToken(Long userId, String username) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + EXPIRATION_MS);
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(KEY)
                .compact();
    }
 // payload deta ko claims bote hai
    // this method is for verification of token
    public static Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(KEY)
                .build()
                .parseClaimsJws(token)// ye automatically wps se key ke sath signature bna ke purana k sath check krta hai sath meh exp bhi check krta hai
                .getBody();
    }
}


