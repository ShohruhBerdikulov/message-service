package com.example.emergencyservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class FileService {

    @Value("${app.data.directory}")
    private String dataDirectory;

    @Value("${app.data.templates-dir}")
    private String templatesDir;

    public FileService() {
    }

    public List<String[]> loadRecipients(String filename) {
        List<String[]> recipients = new ArrayList<>();
        try {
            Path filePath = Paths.get(dataDirectory + filename);
            if (Files.exists(filePath)) {
                List<String> lines = Files.readAllLines(filePath);
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        recipients.add(new String[]{parts[0].trim(), parts[1].trim()});
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading recipients: " + e.getMessage());
        }
        return recipients;
    }

    public void saveRecipients(String filename, List<String[]> recipients) {
        try {
            Path filePath = Paths.get(dataDirectory + filename);
            List<String> lines = new ArrayList<>();
            for (String[] recipient : recipients) {
                lines.add(recipient[0] + ":" + recipient[1]);
            }
            Files.write(filePath, lines);
        } catch (IOException e) {
            System.err.println("Error saving recipients: " + e.getMessage());
        }
    }

    public String loadTemplate(String name) {
        try {
            Path templatePath = Paths.get(templatesDir + name + ".txt");
            if (Files.exists(templatePath)) {
                return Files.readString(templatePath);
            }
        } catch (IOException e) {
            System.err.println("Error loading template: " + e.getMessage());
        }
        return "";
    }

    public void saveTemplate(String name, String content) {
        try {
            Path templatePath = Paths.get(templatesDir + name + ".txt");
            Files.write(templatePath, content.getBytes());
        } catch (IOException e) {
            System.err.println("Error saving template: " + e.getMessage());
        }
    }

    public List<String> getTemplateNames() {
        List<String> templates = new ArrayList<>();
        try {
            Files.list(Paths.get(templatesDir))
                    .filter(path -> path.toString().endsWith(".txt"))
                    .forEach(path -> {
                        String filename = path.getFileName().toString();
                        templates.add(filename.substring(0, filename.length() - 4));
                    });
        } catch (IOException e) {
            System.err.println("Error getting template names: " + e.getMessage());
        }
        return templates;
    }

    public void appendToFile(String filename, String content) {
        try {
            Path filePath = Paths.get(dataDirectory + filename);
            Files.write(filePath, (content + System.lineSeparator()).getBytes(),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Error appending to file: " + e.getMessage());
        }
    }

    public void createDefaultFiles() {
        List<String[]> users = loadRecipients("users.txt");
        if (users.isEmpty()) {
            String hashedPassword = org.springframework.security.crypto.bcrypt.BCrypt.hashpw("admin123",
                    org.springframework.security.crypto.bcrypt.BCrypt.gensalt());
            List<String[]> defaultUsers = new ArrayList<>();
            defaultUsers.add(new String[]{"admin", hashedPassword});
            saveRecipients("users.txt", defaultUsers);
        }

        if (getTemplateNames().isEmpty()) {
            saveTemplate("default", "Сообщение: {content}");
            saveTemplate("urgent", "СРОЧНО: {content}");
            saveTemplate("info", "Информация: {content}");
        }

        if (loadRecipients("telegram_recipients.txt").isEmpty()) {
            List<String[]> telegramRecipients = new ArrayList<>();
            telegramRecipients.add(new String[]{"Рабочая группа", "-1001234567890"});
            telegramRecipients.add(new String[]{"Администраторы", "-1009876543210"});
            saveRecipients("telegram_recipients.txt", telegramRecipients);
        }

        if (loadRecipients("email_recipients.txt").isEmpty()) {
            List<String[]> emailRecipients = new ArrayList<>();
            emailRecipients.add(new String[]{"Менеджер", "manager@example.com"});
            emailRecipients.add(new String[]{"Бухгалтер", "accountant@example.com"});
            saveRecipients("email_recipients.txt", emailRecipients);
        }

        if (loadRecipients("sms_recipients.txt").isEmpty()) {
            List<String[]> smsRecipients = new ArrayList<>();
            smsRecipients.add(new String[]{"Директор", "+998901234567"});
            smsRecipients.add(new String[]{"Менеджер", "+998902345678"});
            saveRecipients("sms_recipients.txt", smsRecipients);
        }
    }
}