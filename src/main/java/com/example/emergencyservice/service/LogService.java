package com.example.emergencyservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class LogService {

    @Value("${app.data.directory}")
    private String dataDirectory;

    private final FileService fileService;

    public LogService(FileService fileService) {
        this.fileService = fileService;
    }

    public void logMessage(String channel, String recipient, String message,
                           String username, String status) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String shortLogEntry = String.format("[%s] %s -> %s: %s - %s",
                timestamp, username, channel, recipient, status);

        String fullLogEntry = String.format("{\"timestamp\": \"%s\", \"user\": \"%s\", " +
                        "\"channel\": \"%s\", \"recipient\": \"%s\", \"message\": \"%s\", \"status\": \"%s\"}",
                timestamp, username, channel, recipient,
                message.replace("\"", "\\\"").replace("\n", " "), status);

        System.out.println(shortLogEntry);

        fileService.appendToFile("logs.txt", fullLogEntry);
    }

    public void logError(String operation, String errorMessage, String username) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String logEntry = String.format("[%s] ERROR - %s: %s - User: %s",
                timestamp, operation, errorMessage, username);

        System.err.println(logEntry);
        fileService.appendToFile("error_logs.txt", logEntry);
    }

    public void logAuthAttempt(String username, boolean success) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String status = success ? "SUCCESS" : "FAILED";
        String logEntry = String.format("[%s] AUTH %s - User: %s",
                timestamp, status, username);

        System.out.println(logEntry);
        fileService.appendToFile("auth_logs.txt", logEntry);
    }
}