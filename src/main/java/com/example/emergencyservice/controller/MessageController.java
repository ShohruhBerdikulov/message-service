package com.example.emergencyservice.controller;

import com.example.emergencyservice.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/")
public class MessageController {

    private final AuthService authService;
    private final MessageService messageService;
    private final FileService fileService;

    public MessageController(AuthService authService, MessageService messageService,
                             FileService fileService) {
        this.authService = authService;
        this.messageService = messageService;
        this.fileService = fileService;
    }

    @GetMapping
    public String home(HttpSession session) {
        if (session.getAttribute("username") != null) {
            return "redirect:/dashboard";
        }
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpSession session,
                        RedirectAttributes redirectAttributes) {
        if (authService.authenticate(username, password)) {
            session.setAttribute("username", username);
            redirectAttributes.addFlashAttribute("success", "Успешный вход в систему");
            return "redirect:/dashboard";
        }
        redirectAttributes.addFlashAttribute("error", "Неверное имя пользователя или пароль");
        return "redirect:/login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        String username = (String) session.getAttribute("username");
        session.invalidate();
        if (username != null) {
            redirectAttributes.addFlashAttribute("success", "Вы успешно вышли из системы");
        }
        return "redirect:/login";
    }

    @PostMapping("/send")
    public String sendMessage(@RequestParam String channel,
                              @RequestParam List<String> recipients, // Endi List<String> qabul qilamiz
                              @RequestParam String template,
                              @RequestParam String message,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            return "redirect:/login";
        }

        if (message == null || message.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Сообщение не может быть пустым");
            return "redirect:/dashboard";
        }

        if (recipients == null || recipients.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Выберите хотя бы одного получателя");
            return "redirect:/dashboard";
        }

        String fullMessage = fileService.loadTemplate(template).replace("{content}", message);
        int successCount = 0;
        int totalCount = recipients.size();

        for (String recipient : recipients) {
            boolean success = false;

            switch (channel) {
                case "telegram":
                    success = messageService.sendTelegramMessage(recipient, fullMessage, username);
                    break;
                case "email":
                    success = messageService.sendEmail(recipient, "Сообщение от сервиса", fullMessage, username);
                    break;
                case "sms":
                    success = messageService.sendSms(recipient, fullMessage, username);
                    break;
            }

            if (success) {
                successCount++;
            }

            // Kichik kechikish (agar kerak bo'lsa)
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (successCount > 0) {
            redirectAttributes.addFlashAttribute("success",
                    "Сообщения отправлены! Успешно: " + successCount + " из " + totalCount);
        } else {
            redirectAttributes.addFlashAttribute("error",
                    "Не удалось отправить сообщения ни одному получателю");
        }

        return "redirect:/dashboard";
    }

    @PostMapping("/save-template")
    public String saveTemplate(@RequestParam("template_name") String templateName,
                               @RequestParam("template_content") String templateContent,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        if (session.getAttribute("username") == null) {
            return "redirect:/login";
        }

        if (templateName == null || templateName.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Название шаблона не может быть пустым");
            return "redirect:/dashboard";
        }

        fileService.saveTemplate(templateName, templateContent);
        redirectAttributes.addFlashAttribute("success", "Шаблон сохранен успешно!");

        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, HttpSession session) {
        if (session.getAttribute("username") == null) {
            return "redirect:/login";
        }

        model.addAttribute("username", session.getAttribute("username"));
        model.addAttribute("templates", fileService.getTemplateNames());

        // Shablonlarning to'liq matnini ham qo'shamiz
        Map<String, String> templateContents = new HashMap<>();
        for (String templateName : fileService.getTemplateNames()) {
            templateContents.put(templateName, fileService.loadTemplate(templateName));
        }
        model.addAttribute("templateContents", templateContents);

        // Recipientlarni ham qo'shamiz
        model.addAttribute("telegramRecipients", fileService.loadRecipients("telegram_recipients.txt"));
        model.addAttribute("emailRecipients", fileService.loadRecipients("email_recipients.txt"));
        model.addAttribute("smsRecipients", fileService.loadRecipients("sms_recipients.txt"));

        return "dashboard";
    }

    @PostMapping("/save-recipients")
    public String saveRecipients(@RequestParam String channel,
                                 @RequestParam String recipients,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        if (session.getAttribute("username") == null) {
            return "redirect:/login";
        }

        String filename = getFilenameForChannel(channel);
        if (filename != null) {
            List<String[]> recipientList = parseRecipients(recipients);
            fileService.saveRecipients(filename, recipientList);
            redirectAttributes.addFlashAttribute("success", "Получатели сохранены успешно!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Неверный канал");
        }

        return "redirect:/dashboard";
    }

    private String getFilenameForChannel(String channel) {
        return switch (channel) {
            case "telegram" -> "telegram_recipients.txt";
            case "email" -> "email_recipients.txt";
            case "sms" -> "sms_recipients.txt";
            default -> null;
        };
    }

    private List<String[]> parseRecipients(String recipientsText) {
        List<String[]> result = new ArrayList<>();
        String[] lines = recipientsText.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && line.contains(":")) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    result.add(new String[]{parts[0].trim(), parts[1].trim()});
                }
            }
        }

        return result;
    }
}