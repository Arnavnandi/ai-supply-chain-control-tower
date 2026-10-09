package com.supplychain.controltower.controller;

import com.supplychain.controltower.dto.auth.RegisterRequest;
import com.supplychain.controltower.entity.Role;
import com.supplychain.controltower.entity.User;
import com.supplychain.controltower.repository.UserRepository;
import com.supplychain.controltower.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    private AuthenticationManager authenticationManager;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider tokenProvider;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tokenProvider = mock(JwtTokenProvider.class);

        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");

        authController = new AuthController(
                authenticationManager,
                userRepository,
                passwordEncoder,
                tokenProvider
        );
    }

    @Test
    void testRegisterUserForcesViewerRolePreventingPrivilegeEscalation() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setEmail("newuser@supplychain.com");
        req.setPassword("secret123");
        req.setRole(Role.ROLE_ADMIN); // Attempt privilege escalation

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("newuser@supplychain.com")).thenReturn(false);

        ResponseEntity<?> response = authController.registerUser(req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("newuser", savedUser.getUsername());
        assertEquals("newuser@supplychain.com", savedUser.getEmail());
        assertEquals("hashed_password", savedUser.getPassword());
        assertEquals(Role.ROLE_VIEWER, savedUser.getRole(), "Public self-registration must force ROLE_VIEWER!");
    }

    @Test
    void testRegisterUserDuplicateUsernameReturnsBadRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("admin");
        req.setEmail("admin@supplychain.com");
        req.setPassword("secret123");

        when(userRepository.existsByUsername("admin")).thenReturn(true);

        ResponseEntity<?> response = authController.registerUser(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(((Map<?, ?>) response.getBody()).get("message").toString().contains("Username is already taken"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void testRegisterUserDuplicateEmailReturnsBadRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("uniqueuser");
        req.setEmail("existing@supplychain.com");
        req.setPassword("secret123");

        when(userRepository.existsByUsername("uniqueuser")).thenReturn(false);
        when(userRepository.existsByEmail("existing@supplychain.com")).thenReturn(true);

        ResponseEntity<?> response = authController.registerUser(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(((Map<?, ?>) response.getBody()).get("message").toString().contains("Email address is already in use"));
        verify(userRepository, never()).save(any());
    }
}
