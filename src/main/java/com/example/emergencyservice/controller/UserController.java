package com.example.emergencyservice.controller;

import com.example.emergencyservice.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/manage")
    public String manageUsers(Model model, HttpSession session) {
        if (session.getAttribute("username") == null) {
            return "redirect:/login";
        }

        model.addAttribute("users", authService.getAllUsers());
        return "user-management";
    }

    @PostMapping("/create")
    public String createUser(@RequestParam String username,
                             @RequestParam String password,
                             @RequestParam String confirmPassword,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {

        if (session.getAttribute("username") == null) {
            return "redirect:/login";
        }

        // Validation
        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Пароли не совпадают");
            return "redirect:/users/manage";
        }

        if (password.length() < 4) {
            redirectAttributes.addFlashAttribute("error", "Пароль должен содержать минимум 4 символа");
            return "redirect:/users/manage";
        }

        // Create user
        boolean success = authService.createUser(username, password);

        if (success) {
            redirectAttributes.addFlashAttribute("success", "Пользователь " + username + " успешно создан");
        } else {
            redirectAttributes.addFlashAttribute("error", "Пользователь " + username + " уже существует");
        }

        return "redirect:/users/manage";
    }

    @PostMapping("/delete")
    public String deleteUser(@RequestParam String username,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {

        if (session.getAttribute("username") == null) {
            return "redirect:/login";
        }

        // Prevent deleting current user
        String currentUser = (String) session.getAttribute("username");
        if (currentUser.equals(username)) {
            redirectAttributes.addFlashAttribute("error", "Нельзя удалить текущего пользователя");
            return "redirect:/users/manage";
        }

        boolean success = authService.deleteUser(username);

        if (success) {
            redirectAttributes.addFlashAttribute("success", "Пользователь " + username + " удален");
        } else {
            redirectAttributes.addFlashAttribute("error", "Пользователь " + username + " не найден");
        }

        return "redirect:/users/manage";
    }
}