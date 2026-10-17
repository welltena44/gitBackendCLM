package com.suivicontrats.front.controller;

import com.suivicontrats.front.dto.LoginRequest;
import com.suivicontrats.front.dto.LoginResponse;
import com.suivicontrats.front.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest body){
        try{ return ResponseEntity.ok(authService.login(body)); }
        catch(RuntimeException e){ return ResponseEntity.status(401).body(Map.of("message", e.getMessage())); }
    }

    @PostMapping("/demande-acces")
    public Map<String,String> demande(@RequestBody Map<String,String> b){
        return Map.of("message","Demande reçue pour "+b.get("email"));
    }
}