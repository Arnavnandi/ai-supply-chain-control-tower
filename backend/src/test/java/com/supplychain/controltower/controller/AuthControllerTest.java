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
    private com.supplychain.controltower.repository.CompanyRepository companyRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider tokenProvider;
    private com.supplychain.controltower.service.EmailOtpService emailOtpService;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        userRepository = mock(UserRepository.class);
        companyRepository = mock(com.supplychain.controltower.repository.CompanyRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tokenProvider = mock(JwtTokenProvider.class);
        emailOtpService = mock(com.supplychain.controltower.service.EmailOtpService.class);

        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
        com.supplychain.controltower.entity.Company demoCompany = new com.supplychain.controltower.entity.Company();
        demoCompany.setId(1L);
        demoCompany.setCode("DEMO");
        demoCompany.setName("Demo Supply Chain Corp");
        when(companyRepository.findByCode("DEMO")).thenReturn(java.util.Optional.of(demoCompany));

        authController = new AuthController(
                authenticationManager,
                userRepository,
                companyRepository,
                passwordEncoder,
                tokenProvider,
                emailOtpService
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

    @Test
    void testRegisterNewCompanyAssignsAdminRoleAndCreatesCompany() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newadmin");
        req.setEmail("admin@acme.com");
        req.setPassword("secret123");
        req.setCompanyName("Acme Logistics Inc");

        when(userRepository.existsByUsername("newadmin")).thenReturn(false);
        when(userRepository.existsByEmail("admin@acme.com")).thenReturn(false);
        when(companyRepository.existsByName("Acme Logistics Inc")).thenReturn(false);

        com.supplychain.controltower.entity.Company createdCompany = com.supplychain.controltower.entity.Company.builder()
                .id(10L)
                .name("Acme Logistics Inc")
                .code("COMP-ACME1")
                .status("ACTIVE")
                .build();
        when(companyRepository.save(any(com.supplychain.controltower.entity.Company.class))).thenReturn(createdCompany);

        ResponseEntity<?> response = authController.registerUser(req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals(Role.ROLE_ADMIN, savedUser.getRole(), "New company creator must be assigned ROLE_ADMIN!");
        assertNotNull(savedUser.getCompany());
        assertEquals("Acme Logistics Inc", savedUser.getCompany().getName());
    }

    @Test
    void testRegisterJoinExistingCompanyAssignsViewerRole() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("joiner");
        req.setEmail("joiner@supplychain.com");
        req.setPassword("secret123");
        req.setCompanyCode("COMP-EXISTING");

        com.supplychain.controltower.entity.Company existingCompany = com.supplychain.controltower.entity.Company.builder()
                .id(20L)
                .name("Existing Corp")
                .code("COMP-EXISTING")
                .status("ACTIVE")
                .build();

        when(userRepository.existsByUsername("joiner")).thenReturn(false);
        when(userRepository.existsByEmail("joiner@supplychain.com")).thenReturn(false);
        when(companyRepository.findByCode("COMP-EXISTING")).thenReturn(java.util.Optional.of(existingCompany));

        ResponseEntity<?> response = authController.registerUser(req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals(Role.ROLE_VIEWER, savedUser.getRole(), "Joining user must be assigned ROLE_VIEWER!");
        assertEquals(20L, savedUser.getCompany().getId());
    }

    @Test
    void testRegisterJoinSuspendedCompanyReturnsBadRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("suspendeduser");
        req.setEmail("suspended@supplychain.com");
        req.setPassword("secret123");
        req.setCompanyCode("COMP-SUSPENDED");

        com.supplychain.controltower.entity.Company suspendedComp = com.supplychain.controltower.entity.Company.builder()
                .id(30L)
                .name("Suspended Corp")
                .code("COMP-SUSPENDED")
                .status("SUSPENDED")
                .build();

        when(userRepository.existsByUsername("suspendeduser")).thenReturn(false);
        when(userRepository.existsByEmail("suspended@supplychain.com")).thenReturn(false);
        when(companyRepository.findByCode("COMP-SUSPENDED")).thenReturn(java.util.Optional.of(suspendedComp));

        ResponseEntity<?> response = authController.registerUser(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(((Map<?, ?>) response.getBody()).get("message").toString().contains("suspended"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void testLoginValidCredentialsReturnsMfaChallengeWithoutJwt() {
        com.supplychain.controltower.dto.auth.LoginRequest loginReq = new com.supplychain.controltower.dto.auth.LoginRequest();
        loginReq.setUsernameOrEmail("user@company.com");
        loginReq.setPassword("Secret123!");

        User mockUser = User.builder()
                .id(42L)
                .username("user")
                .email("user@company.com")
                .role(Role.ROLE_ADMIN)
                .build();

        com.supplychain.controltower.security.UserPrincipal principal =
                com.supplychain.controltower.security.UserPrincipal.create(mockUser);

        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userRepository.findById(42L)).thenReturn(java.util.Optional.of(mockUser));

        com.supplychain.controltower.dto.auth.LoginInitiationResponse initiation =
                com.supplychain.controltower.dto.auth.LoginInitiationResponse.builder()
                        .mfaRequired(true)
                        .mfaToken("test-mfa-token-uuid")
                        .maskedEmail("u***r@company.com")
                        .expiresInSeconds(300)
                        .resendCooldownSeconds(60)
                        .build();

        when(emailOtpService.initiateOtpChallenge(mockUser)).thenReturn(initiation);

        ResponseEntity<com.supplychain.controltower.dto.auth.LoginInitiationResponse> response =
                authController.authenticateUser(loginReq);

        assertNotNull(response.getBody());
        assertTrue(response.getBody().isMfaRequired());
        assertEquals("test-mfa-token-uuid", response.getBody().getMfaToken());
        assertEquals("u***r@company.com", response.getBody().getMaskedEmail());
        verify(tokenProvider, never()).generateToken(any());
    }

    @Test
    void testVerifyOtpValidCodeIssuesJwtWithPreservedTenantAndRoleClaims() {
        com.supplychain.controltower.dto.auth.VerifyOtpRequest verifyReq =
                new com.supplychain.controltower.dto.auth.VerifyOtpRequest("test-mfa-token-uuid", "123456");

        com.supplychain.controltower.entity.Company tenantComp = com.supplychain.controltower.entity.Company.builder()
                .id(88L)
                .name("Acme Logistics")
                .code("COMP-ACME")
                .status("ACTIVE")
                .build();

        User mockUser = User.builder()
                .id(42L)
                .username("acme_admin")
                .email("admin@acme.com")
                .role(Role.ROLE_ADMIN)
                .company(tenantComp)
                .build();

        when(emailOtpService.verifyOtp("test-mfa-token-uuid", "123456")).thenReturn(mockUser);
        when(tokenProvider.generateToken(any())).thenReturn("signed.jwt.token");

        ResponseEntity<com.supplychain.controltower.dto.auth.AuthResponse> response =
                authController.verifyOtp(verifyReq);

        assertNotNull(response.getBody());
        assertEquals("signed.jwt.token", response.getBody().getToken());
        assertEquals(42L, response.getBody().getId());
        assertEquals("ROLE_ADMIN", response.getBody().getRole());
        assertEquals(88L, response.getBody().getCompanyId());
        assertEquals("COMP-ACME", response.getBody().getCompanyCode());
        assertEquals("Acme Logistics", response.getBody().getCompanyName());
    }
}
