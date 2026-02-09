package com.university.timetable.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Email service using Brevo (Sendinblue) API for sending transactional emails.
 * Templates are loaded from src/main/resources/templates/email/
 */
@Service
@Slf4j
public class EmailService {

    @Value("${brevo.api-key:}")
    private String apiKey;

    @Value("${brevo.sender-email:noreply@babcock.edu.ng}")
    private String senderEmail;

    @Value("${brevo.sender-name:University Timetable}")
    private String senderName;

    @Value("${app.name:University Timetable System}")
    private String systemName;

    @Value("${app.login-url:http://localhost:4200/login}")
    private String loginUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    /**
     * Send welcome email to a newly created user with their login credentials.
     * Runs asynchronously to not block the main thread.
     */
    @Async
    public void sendWelcomeEmail(String email, String firstName, String lastName, String temporaryPassword) {
        try {
            String htmlContent = loadAndProcessTemplate("welcome.html", Map.of(
                    "firstName", firstName,
                    "lastName", lastName,
                    "email", email,
                    "temporaryPassword", temporaryPassword,
                    "systemName", systemName,
                    "loginUrl", loginUrl,
                    "year", String.valueOf(Year.now().getValue())));

            sendEmail(email, firstName + " " + lastName,
                    "Welcome to " + systemName + " - Your Login Credentials",
                    htmlContent);

            log.info("Welcome email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", email, e.getMessage());
        }
    }

    /**
     * Send password reset email with new password.
     * Runs asynchronously to not block the main thread.
     */
    @Async
    public void sendPasswordResetEmail(String email, String firstName, String lastName, String newPassword) {
        try {
            String htmlContent = loadAndProcessTemplate("password-reset.html", Map.of(
                    "firstName", firstName,
                    "lastName", lastName,
                    "email", email,
                    "temporaryPassword", newPassword,
                    "systemName", systemName,
                    "loginUrl", loginUrl,
                    "year", String.valueOf(Year.now().getValue())));

            sendEmail(email, firstName + " " + lastName,
                    "Password Reset - " + systemName,
                    htmlContent);

            log.info("Password reset email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", email, e.getMessage());
        }
    }

    /**
     * Load an email template and substitute variables.
     * Variables in template use {{variableName}} format.
     */
    private String loadAndProcessTemplate(String templateName, Map<String, String> variables) throws IOException {
        ClassPathResource resource = new ClassPathResource("templates/email/" + templateName);
        String template;

        try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            template = FileCopyUtils.copyToString(reader);
        }

        // Replace all variables
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }

        return template;
    }

    /**
     * Send email via Brevo API.
     */
    private void sendEmail(String toEmail, String toName, String subject, String htmlContent) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Brevo API key not configured. Email to {} not sent. Subject: {}", toEmail, subject);
            log.info("Email content would have been: {}",
                    htmlContent.substring(0, Math.min(200, htmlContent.length())));
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);

        Map<String, Object> payload = new HashMap<>();
        payload.put("sender", Map.of("name", senderName, "email", senderEmail));
        payload.put("to", List.of(Map.of("email", toEmail, "name", toName)));
        payload.put("subject", subject);
        payload.put("htmlContent", htmlContent);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(BREVO_API_URL, request, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Email sent successfully to {}", toEmail);
            } else {
                log.warn("Email API returned non-success status: {} for {}", response.getStatusCode(), toEmail);
            }
        } catch (Exception e) {
            log.error("Failed to send email via Brevo API: {}", e.getMessage());
        }
    }

    /**
     * Check if email service is properly configured.
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
