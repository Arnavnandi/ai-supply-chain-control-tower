package com.supplychain.controltower.controller;

import com.supplychain.controltower.dto.auth.AuthResponse;
import com.supplychain.controltower.dto.auth.LoginRequest;
import com.supplychain.controltower.dto.auth.RegisterRequest;
import com.supplychain.controltower.entity.Role;
import com.supplychain.controltower.entity.User;
import com.supplychain.controltower.repository.UserRepository;
import com.supplychain.controltower.security.JwtTokenProvider;
import com.supplychain.controltower.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final com.supplychain.controltower.repository.CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsernameOrEmail(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        return ResponseEntity.ok(AuthResponse.builder()
                .token(jwt)
                .tokenType("Bearer")
                .id(userPrincipal.getId())
                .username(userPrincipal.getUsername())
                .email(userPrincipal.getEmail())
                .role(userPrincipal.getAuthorities().iterator().next().getAuthority())
                .build());
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username is already taken!"));
        }

        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email address is already in use!"));
        }

        com.supplychain.controltower.entity.Company company = null;
        Role assignedRole = Role.ROLE_VIEWER;

        // 1. If companyName is provided, register a NEW company and make user initial ADMIN
        if (registerRequest.getCompanyName() != null && !registerRequest.getCompanyName().isBlank()) {
            String cleanName = registerRequest.getCompanyName().trim();
            if (companyRepository.existsByName(cleanName)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Company name is already registered!"));
            }

            String generatedCode = "COMP-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            company = com.supplychain.controltower.entity.Company.builder()
                    .name(cleanName)
                    .code(generatedCode)
                    .status("ACTIVE")
                    .build();
            company = companyRepository.save(company);
            assignedRole = Role.ROLE_ADMIN; // Initial company creator gets ADMIN
        }
        // 2. Else if companyCode is provided, join an EXISTING active company as VIEWER
        else if (registerRequest.getCompanyCode() != null && !registerRequest.getCompanyCode().isBlank()) {
            Optional<com.supplychain.controltower.entity.Company> existingComp = companyRepository.findByCode(registerRequest.getCompanyCode().trim());
            if (existingComp.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid company join code!"));
            }
            if ("SUSPENDED".equalsIgnoreCase(existingComp.get().getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Company account is suspended. Join denied."));
            }
            company = existingComp.get();
            assignedRole = Role.ROLE_VIEWER; // Joining users always default to VIEWER
        }
        // 3. Fallback: Default to Demo Company as VIEWER
        else {
            company = companyRepository.findById(1L).orElse(null);
            assignedRole = Role.ROLE_VIEWER;
        }

        User user = User.builder()
                .username(registerRequest.getUsername())
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .role(assignedRole)
                .company(company)
                .build();

        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "User registered successfully!",
                "companyCode", company != null ? company.getCode() : "DEMO",
                "companyName", company != null ? company.getName() : "Demo Corp",
                "role", assignedRole.name()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }
        return ResponseEntity.ok(Map.of(
                "id", userPrincipal.getId(),
                "username", userPrincipal.getUsername(),
                "email", userPrincipal.getEmail(),
                "role", userPrincipal.getAuthorities().iterator().next().getAuthority()
        ));
    }
}
