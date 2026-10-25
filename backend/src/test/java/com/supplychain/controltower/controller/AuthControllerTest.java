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
    void testLoginVerifiedUserReturnsJwtDirectlyWithoutOtpChallenge() {
        com.supplychain.controltower.dto.auth.LoginRequest loginReq = new com.supplychain.controltower.dto.auth.LoginRequest();
        loginReq.setUsernameOrEmail("verified_user@company.com");
        loginReq.setPassword("Secret123!");

        com.supplychain.controltower.entity.Company comp = com.supplychain.controltower.entity.Company.builder()
                .id(10L)
                .name("Acme Corp")
                .code("COMP-ACME")
                .status("ACTIVE")
                .build();

        User mockUser = User.builder()
                .id(42L)
                .username("verified_user")
                .email("verified_user@company.com")
                .role(Role.ROLE_ADMIN)
                .company(comp)
                .build();

        com.supplychain.controltower.security.UserPrincipal principal =
                com.supplychain.controltower.security.UserPrincipal.create(mockUser);

        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userRepository.findById(42L)).thenReturn(java.util.Optional.of(mockUser));
        when(emailOtpService.isUserEmailVerified(mockUser)).thenReturn(true);
        when(tokenProvider.generateToken(any())).thenReturn("direct.jwt.token");

        ResponseEntity<?> response = authController.authenticateUser(loginReq);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof com.supplychain.controltower.dto.auth.AuthResponse);
        com.supplychain.controltower.dto.auth.AuthResponse authResp = (com.supplychain.controltower.dto.auth.AuthResponse) response.getBody();
        assertEquals("direct.jwt.token", authResp.getToken());
        assertEquals(42L, authResp.getId());
        assertEquals("verified_user", authResp.getUsername());
        assertEquals("verified_user@company.com", authResp.getEmail());
        assertEquals("ROLE_ADMIN", authResp.getRole());
        assertEquals(10L, authResp.getCompanyId());
        assertEquals("COMP-ACME", authResp.getCompanyCode());
        assertFalse(authResp.isMfaRequired(), "MFA must not be required for verified accounts");

        verify(tokenProvider, times(1)).generateToken(auth);
        verify(emailOtpService, never()).initiateOtpChallenge(any());
    }

    @Test
    void testLoginUnverifiedUserReturnsMfaChallengeWithoutJwt() {
        com.supplychain.controltower.dto.auth.LoginRequest loginReq = new com.supplychain.controltower.dto.auth.LoginRequest();
        loginReq.setUsernameOrEmail("newuser@company.com");
        loginReq.setPassword("Secret123!");

        User mockUser = User.builder()
                .id(43L)
                .username("newuser")
                .email("newuser@company.com")
                .role(Role.ROLE_VIEWER)
                .build();

        com.supplychain.controltower.security.UserPrincipal principal =
                com.supplychain.controltower.security.UserPrincipal.create(mockUser);

        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userRepository.findById(43L)).thenReturn(java.util.Optional.of(mockUser));
        when(emailOtpService.isUserEmailVerified(mockUser)).thenReturn(false);

        com.supplychain.controltower.dto.auth.LoginInitiationResponse initiation =
                com.supplychain.controltower.dto.auth.LoginInitiationResponse.builder()
                        .mfaRequired(true)
                        .mfaToken("test-mfa-token-uuid")
                        .maskedEmail("n***r@company.com")
                        .expiresInSeconds(300)
                        .resendCooldownSeconds(60)
                        .build();

        when(emailOtpService.initiateOtpChallenge(mockUser)).thenReturn(initiation);

        ResponseEntity<?> response = authController.authenticateUser(loginReq);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof com.supplychain.controltower.dto.auth.LoginInitiationResponse);
        com.supplychain.controltower.dto.auth.LoginInitiationResponse mfaResp = (com.supplychain.controltower.dto.auth.LoginInitiationResponse) response.getBody();
        assertTrue(mfaResp.isMfaRequired());
        assertEquals("test-mfa-token-uuid", mfaResp.getMfaToken());
        assertEquals("n***r@company.com", mfaResp.getMaskedEmail());

        verify(tokenProvider, never()).generateToken(any());
        verify(emailOtpService, times(1)).initiateOtpChallenge(mockUser);
    }

    @Test
    void testLoginInvalidCredentialsThrowsBadCredentialsExceptionWithoutSendingOtp() {
        com.supplychain.controltower.dto.auth.LoginRequest loginReq = new com.supplychain.controltower.dto.auth.LoginRequest();
        loginReq.setUsernameOrEmail("user@company.com");
        loginReq.setPassword("WrongPassword!");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("Bad credentials"));

        assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () ->
                authController.authenticateUser(loginReq));

        verify(emailOtpService, never()).initiateOtpChallenge(any());
        verify(tokenProvider, never()).generateToken(any());
    }

    @Test
    void testLoginSeedAdminReturnsJwtDirectlyWithoutOtpChallenge() {
        com.supplychain.controltower.dto.auth.LoginRequest loginReq = new com.supplychain.controltower.dto.auth.LoginRequest();
        loginReq.setUsernameOrEmail("admin");
        loginReq.setPassword("admin123");

        User adminUser = User.builder()
                .id(1L)
                .username("admin")
                .email("admin@supplychain.com")
                .role(Role.ROLE_ADMIN)
                .build();

        com.supplychain.controltower.security.UserPrincipal principal =
                com.supplychain.controltower.security.UserPrincipal.create(adminUser);

        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(adminUser));
        when(emailOtpService.isUserEmailVerified(adminUser)).thenReturn(true);
        when(tokenProvider.generateToken(any())).thenReturn("admin.jwt.token");

        ResponseEntity<?> response = authController.authenticateUser(loginReq);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof com.supplychain.controltower.dto.auth.AuthResponse);
        com.supplychain.controltower.dto.auth.AuthResponse authResp = (com.supplychain.controltower.dto.auth.AuthResponse) response.getBody();
        assertEquals("admin.jwt.token", authResp.getToken());
        assertFalse(authResp.isMfaRequired());
        verify(emailOtpService, never()).initiateOtpChallenge(any());
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

    @Test
    void testVerifyOtpInvalidCodeThrowsBadCredentialsException() {
        com.supplychain.controltower.dto.auth.VerifyOtpRequest verifyReq =
                new com.supplychain.controltower.dto.auth.VerifyOtpRequest("test-mfa-token-uuid", "000000");

        when(emailOtpService.verifyOtp("test-mfa-token-uuid", "000000"))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("Invalid verification code. 2 attempt(s) remaining."));

        org.springframework.security.authentication.BadCredentialsException ex =
                assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () ->
                        authController.verifyOtp(verifyReq));

        assertEquals("Invalid verification code. 2 attempt(s) remaining.", ex.getMessage());

        // Verify GlobalExceptionHandler translates it to HTTP 401 Unauthorized
        com.supplychain.controltower.exception.GlobalExceptionHandler handler =
                new com.supplychain.controltower.exception.GlobalExceptionHandler();
        ResponseEntity<com.supplychain.controltower.exception.ErrorResponseDto> errorResp =
                handler.handleBadCredentialsException(ex);
        assertEquals(HttpStatus.UNAUTHORIZED, errorResp.getStatusCode());
        assertEquals("Invalid verification code. 2 attempt(s) remaining.", errorResp.getBody().getMessage());
    }

    @Test
    void testVerifyOtpExpiredCodeThrowsBadCredentialsException() {
        com.supplychain.controltower.dto.auth.VerifyOtpRequest verifyReq =
                new com.supplychain.controltower.dto.auth.VerifyOtpRequest("expired-token", "123456");

        when(emailOtpService.verifyOtp("expired-token", "123456"))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("Verification code has expired. Please request a new code."));

        org.springframework.security.authentication.BadCredentialsException ex =
                assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () ->
                        authController.verifyOtp(verifyReq));

        assertEquals("Verification code has expired. Please request a new code.", ex.getMessage());

        com.supplychain.controltower.exception.GlobalExceptionHandler handler =
                new com.supplychain.controltower.exception.GlobalExceptionHandler();
        ResponseEntity<com.supplychain.controltower.exception.ErrorResponseDto> errorResp =
                handler.handleBadCredentialsException(ex);
        assertEquals(HttpStatus.UNAUTHORIZED, errorResp.getStatusCode());
        assertEquals("Verification code has expired. Please request a new code.", errorResp.getBody().getMessage());
    }

    @Test
    void testVerifyOtpReusedCodeThrowsBadCredentialsException() {
        com.supplychain.controltower.dto.auth.VerifyOtpRequest verifyReq =
                new com.supplychain.controltower.dto.auth.VerifyOtpRequest("reused-token", "123456");

        when(emailOtpService.verifyOtp("reused-token", "123456"))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("This verification code has already been used. Please log in again."));

        org.springframework.security.authentication.BadCredentialsException ex =
                assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () ->
                        authController.verifyOtp(verifyReq));

        assertEquals("This verification code has already been used. Please log in again.", ex.getMessage());

        com.supplychain.controltower.exception.GlobalExceptionHandler handler =
                new com.supplychain.controltower.exception.GlobalExceptionHandler();
        ResponseEntity<com.supplychain.controltower.exception.ErrorResponseDto> errorResp =
                handler.handleBadCredentialsException(ex);
        assertEquals(HttpStatus.UNAUTHORIZED, errorResp.getStatusCode());
        assertEquals("This verification code has already been used. Please log in again.", errorResp.getBody().getMessage());
    }

    @Test
    void testVerifyOtpSessionLockedAfterMaxAttempts() {
        com.supplychain.controltower.dto.auth.VerifyOtpRequest verifyReq =
                new com.supplychain.controltower.dto.auth.VerifyOtpRequest("locked-token", "123456");

        when(emailOtpService.verifyOtp("locked-token", "123456"))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("Too many failed attempts. Verification session locked. Please log in again."));

        org.springframework.security.authentication.BadCredentialsException ex =
                assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () ->
                        authController.verifyOtp(verifyReq));

        assertEquals("Too many failed attempts. Verification session locked. Please log in again.", ex.getMessage());

        com.supplychain.controltower.exception.GlobalExceptionHandler handler =
                new com.supplychain.controltower.exception.GlobalExceptionHandler();
        ResponseEntity<com.supplychain.controltower.exception.ErrorResponseDto> errorResp =
                handler.handleBadCredentialsException(ex);
        assertEquals(HttpStatus.UNAUTHORIZED, errorResp.getStatusCode());
        assertEquals("Too many failed attempts. Verification session locked. Please log in again.", errorResp.getBody().getMessage());
    }

    @Test
    void testSubsequentLoginAfterOtpVerificationReturnsJwtDirectlyWithoutOtp() {
        // Setup verified user
        User user = User.builder()
                .id(99L)
                .username("registered_user")
                .email("user@tenant.com")
                .role(Role.ROLE_VIEWER)
                .build();

        com.supplychain.controltower.security.UserPrincipal principal =
                com.supplychain.controltower.security.UserPrincipal.create(user);

        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userRepository.findById(99L)).thenReturn(java.util.Optional.of(user));
        // User has already completed OTP verification (isUserEmailVerified returns true)
        when(emailOtpService.isUserEmailVerified(user)).thenReturn(true);
        when(tokenProvider.generateToken(any())).thenReturn("verified.subsequent.jwt");

        com.supplychain.controltower.dto.auth.LoginRequest loginReq = new com.supplychain.controltower.dto.auth.LoginRequest();
        loginReq.setUsernameOrEmail("user@tenant.com");
        loginReq.setPassword("Password123!");

        // Subsequent sign-in
        ResponseEntity<?> loginResponse = authController.authenticateUser(loginReq);

        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        assertTrue(loginResponse.getBody() instanceof com.supplychain.controltower.dto.auth.AuthResponse);
        com.supplychain.controltower.dto.auth.AuthResponse authResp =
                (com.supplychain.controltower.dto.auth.AuthResponse) loginResponse.getBody();

        assertEquals("verified.subsequent.jwt", authResp.getToken());
        assertFalse(authResp.isMfaRequired(), "Subsequent login must not require OTP");
        assertEquals("registered_user", authResp.getUsername());

        // Confirm OTP challenge was NEVER dispatched on subsequent sign-in
        verify(emailOtpService, never()).initiateOtpChallenge(any());
    }

    @Test
    void testGetCurrentUser_ReturnsTenantContextDetails() {
        com.supplychain.controltower.security.UserPrincipal principal =
                new com.supplychain.controltower.security.UserPrincipal(
                        50L,
                        "session_user",
                        "session@company.com",
                        "password",
                        5L,
                        "COMP-ACME",
                        "Acme Global Logistics",
                        "ACTIVE",
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SUPPLY_CHAIN_MANAGER"))
                );

        ResponseEntity<?> response = authController.getCurrentUser(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map<?, ?>);
        Map<?, ?> body = (Map<?, ?>) response.getBody();

        assertEquals(50L, body.get("id"));
        assertEquals("session_user", body.get("username"));
        assertEquals("session@company.com", body.get("email"));
        assertEquals("ROLE_SUPPLY_CHAIN_MANAGER", body.get("role"));
        assertEquals(5L, body.get("companyId"), "Session restoration must return companyId");
        assertEquals("COMP-ACME", body.get("companyCode"), "Session restoration must return companyCode");
        assertEquals("Acme Global Logistics", body.get("companyName"), "Session restoration must return companyName");
    }

    @Test
    void testGetCurrentUser_Unauthenticated_ReturnsUnauthorized() {
        ResponseEntity<?> response = authController.getCurrentUser(null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map<?, ?>);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("Unauthorized", body.get("message"));
    }
}
