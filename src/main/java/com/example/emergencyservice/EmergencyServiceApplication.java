package com.example.emergencyservice;

import com.example.emergencyservice.config.SmsConfig;
import com.example.emergencyservice.config.TelegramConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@SpringBootApplication
@EnableConfigurationProperties({TelegramConfig.class, SmsConfig.class})
public class EmergencyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmergencyServiceApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createDataDirectories() throws IOException {
        Files.createDirectories(Paths.get("./data/templates"));
        Files.createDirectories(Paths.get("./data/logs"));
        System.out.println("Data directories created successfully");
    }
}