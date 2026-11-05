package com.foodloop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends email when app.mail.enabled=true. Otherwise (default, local development) the message is only
 * written to the application log, so the project works without any SMTP credentials.
 */
@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean enabled;
    private final String from;
    private final String frontendBaseUrl;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${app.mail.enabled:false}") boolean enabled,
                        @Value("${app.mail.from:no-reply@foodloop.local}") String from,
                        @Value("${app.frontend-base-url:http://localhost:5500}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public void sendPasswordReset(String to, String token) {
        String link = frontendBaseUrl + "/pages/public/reset-password.html?token=" + token;
        send(to, "FoodLoop AI - reset your password",
                "Use this link to reset your password (valid for 30 minutes):\n" + link
                        + "\n\nIf you did not request this, ignore this email.");
    }

    public void send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!enabled || sender == null) {
            log.info("[DEV EMAIL - not sent] to={} subject={}\n{}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            sender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());   // never fail the request because of email
        }
    }
}
