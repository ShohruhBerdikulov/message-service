package com.example.emergencyservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.logging.Level;

@Service
public class AuthService {

    private static final Logger log = Logger.getLogger(AuthService.class.getName());

    private final FileService fileService;
    private final LogService logService;

    public AuthService(FileService fileService, LogService logService) {
        this.fileService = fileService;
        this.logService = logService;
        initializeDefaultData();
    }

    /**
     * Main authentication method with LDAP primary and file fallback
     */
    public boolean authenticate(String username, String password) {
        return authenticateWithFile(username, password);
    }

    /**
     * File-based authentication (original implementation)
     */
    private boolean authenticateWithFile(String username, String password) {
        List<String[]> users = fileService.loadRecipients("users.txt");

        for (String[] user : users) {
            if (user[0].equals(username)) {
                boolean success = BCrypt.checkpw(password, user[1]);
                logService.logAuthAttempt(username, success);
                logService.logMessage("file", "auth_attempt",
                        "File authentication " + (success ? "successful" : "failed") + " for: " + username,
                        username, success ? "success" : "failure");
                return success;
            }
        }

        logService.logAuthAttempt(username, false);
        logService.logMessage("file", "auth_attempt",
                "File authentication failed - user not found: " + username,
                username, "failure");
        return false;
    }

    public boolean createUser(String username, String password) {
        List<String[]> users = fileService.loadRecipients("users.txt");

        for (String[] user : users) {
            if (user[0].equals(username)) {
                return false;
            }
        }

        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        users.add(new String[]{username, hashedPassword});
        fileService.saveRecipients("users.txt", users);

        logService.logMessage("system", "new_user", "User created: " + username, "system", "success");
        return true;
    }

    public boolean deleteUser(String username) {
        List<String[]> users = fileService.loadRecipients("users.txt");

        boolean removed = users.removeIf(user -> user[0].equals(username));

        if (removed) {
            fileService.saveRecipients("users.txt", users);
            logService.logMessage("system", "delete_user", "User deleted: " + username, "system", "success");
            return true;
        }

        return false;
    }

    public List<String[]> getAllUsers() {
        return fileService.loadRecipients("users.txt");
    }

    private void initializeDefaultData() {
        fileService.createDefaultFiles();
    }
}