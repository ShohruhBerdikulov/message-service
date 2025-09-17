package com.example.emergencyservice.service;

import com.example.emergencyservice.config.TelegramConfig;
import com.example.emergencyservice.config.SmsConfig;
import org.springframework.http.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class MessageService {

    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate;
    private final TelegramConfig telegramConfig;
    private final SmsConfig smsConfig;
    private final LogService logService;

    public MessageService(JavaMailSender mailSender, RestTemplate restTemplate,
                          TelegramConfig telegramConfig, SmsConfig smsConfig,
                          LogService logService) {
        this.mailSender = mailSender;
        this.restTemplate = restTemplate;
        this.telegramConfig = telegramConfig;
        this.smsConfig = smsConfig;
        this.logService = logService;
    }

    public boolean sendTelegramMessage(String chatId, String message, String username) {
        try {
            String token = telegramConfig.getBot().getToken();
            if (token == null || token.isEmpty()) {
                logService.logMessage("telegram", chatId, message, username, "error: Telegram token not configured");
                return false;
            }

            String url = "https://api.telegram.org/bot" + token + "/sendMessage";

            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", message);
            payload.put("parse_mode", "HTML");

            ResponseEntity<String> response = restTemplate.postForEntity(url, payload, String.class);

            boolean success = response.getStatusCode().is2xxSuccessful();
            logService.logMessage("telegram", chatId, message, username,
                    success ? "success" : "error: " + response.getBody());

            return success;
        } catch (Exception e) {
            logService.logMessage("telegram", chatId, message, username, "error: " + e.getMessage());
            return false;
        }
    }

    public boolean sendEmail(String email, String subject, String message, String username) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo(email);
            mailMessage.setSubject(subject);
            mailMessage.setText(message);

            mailSender.send(mailMessage);

            logService.logMessage("email", email, message, username, "success");
            return true;
        } catch (Exception e) {
            logService.logMessage("email", email, message, username, "error: " + e.getMessage());
            return false;
        }
    }

    public boolean sendSms(String phoneNumber, String message, String username) {
        if (!smsConfig.getApi().isEnabled()) {
            logService.logMessage("sms", phoneNumber, message, username, "error: SMS service disabled");
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + smsConfig.getApi().getToken());

            Map<String, String> payload = new HashMap<>();
            payload.put("phone", phoneNumber);
            payload.put("message", message);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(smsConfig.getApi().getUrl(), request, String.class);

            boolean success = response.getStatusCode().is2xxSuccessful();
            logService.logMessage("sms", phoneNumber, message, username,
                    success ? "success" : "error: " + response.getBody());

            return success;
        } catch (Exception e) {
            logService.logMessage("sms", phoneNumber, message, username, "error: " + e.getMessage());
            return false;
        }
    }
}