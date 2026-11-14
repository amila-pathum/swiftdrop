package com.swiftdrop.notification.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class GmailSmtpLiveTest {

    private static Map<String, String> loadEnv() {
        Map<String, String> map = new HashMap<>();
        Path envPath = Paths.get("../../.env");
        if (Files.exists(envPath)) {
            try {
                for (String line : Files.readAllLines(envPath)) {
                    line = line.trim();
                    if (!line.startsWith("#") && line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        map.put(parts[0].trim(), parts[1].trim());
                    }
                }
            } catch (IOException e) {
                // ignore
            }
        }
        return map;
    }

    @Test
    @Disabled("Manual live SMTP verification test")
    void testSendLiveEmail() {
        Map<String, String> env = loadEnv();
        String username = env.getOrDefault("MAIL_USERNAME", System.getenv("MAIL_USERNAME"));
        String password = env.getOrDefault("MAIL_PASSWORD", System.getenv("MAIL_PASSWORD"));

        if (username == null || password == null) {
            System.out.println("Skipping live test: MAIL_USERNAME or MAIL_PASSWORD not found");
            return;
        }

        // Clean password by stripping spaces if present
        String cleanPassword = password.replace(" ", "");

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("smtp.gmail.com");
        mailSender.setPort(587);
        mailSender.setUsername(username);
        mailSender.setPassword(cleanPassword);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(username);
        msg.setTo(username);
        msg.setSubject("SwiftDrop SMTP Test: Status Update Verified");
        msg.setText("Hello!\n\nThis is a verification test from SwiftDrop notification-service.\nYour Gmail SMTP integration is working perfectly!\n\nBest regards,\nSwiftDrop Notification Service");

        System.out.println("Attempting to send email via smtp.gmail.com:587 to " + username + "...");
        mailSender.send(msg);
        System.out.println("SUCCESS! Email sent successfully to " + username);
    }
}
