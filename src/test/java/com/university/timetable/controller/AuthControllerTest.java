package com.university.timetable.controller;

import com.university.timetable.dto.LoginRequest;
import com.university.timetable.dto.LoginResponse;
import com.university.timetable.service.AuditLogService;
import com.university.timetable.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AuthController authController;

    @Test
    void loginReturnsOkAndLogsOnSuccess() {
        LoginRequest request = new LoginRequest("Admin@Example.com", "secret");
        LoginResponse expected = LoginResponse.builder()
                .accessToken("access")
                .refreshToken("refresh")
                .tokenType("Bearer")
                .expiresIn(3600)
                .build();
        when(authService.login(request)).thenReturn(expected);

        var response = authController.login(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, response.getBody());
        verify(auditLogService).logActionSync(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void loginReturns401OnBadCredentials() {
        LoginRequest request = new LoginRequest("admin@example.com", "wrong");
        when(authService.login(request)).thenThrow(new BadCredentialsException("bad"));

        var response = authController.login(request);

        assertEquals(401, response.getStatusCode().value());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals("Authentication Failed", body.get("error"));
        verify(auditLogService).logActionSync(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any());
    }
}
