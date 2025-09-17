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

    private static final String LDAP_CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    private static final String DOMAIN_SUFFIX = "@hq.paynet.uz";
    private static final String TARGET_GROUP = "CN=office-app";
    private static final String[] MEMBER_OF_ATTRIBUTE = {"memberOf"};

    private final FileService fileService;
    private final LogService logService;

    @Value("${ldap.url:ldap://your-ldap-server:389}")
    private String ldapUrl;

    @Value("${ldap.base:DC=hq,DC=paynet,DC=uz}")
    private String ldapBase;

    @Value("${ldap.enabled:true}")
    private boolean ldapEnabled;

    @Value("${ldap.timeout:5000}")
    private int ldapTimeout;

    public AuthService(FileService fileService, LogService logService) {
        this.fileService = fileService;
        this.logService = logService;
        initializeDefaultData();
    }

    /**
     * Main authentication method with LDAP primary and file fallback
     */
    public boolean authenticate(String username, String password) {
        if (ldapEnabled) {
            var ldapResult = authenticateWithLDAP(username, password);
            if (ldapResult.isPresent()) {
                logService.logAuthAttempt(username, ldapResult.get());
                return ldapResult.get();
            }
            log.info("LDAP authentication unavailable, falling back to file authentication for: " + username);
        }

        return authenticateWithFile(username, password);
    }

    /**
     * LDAP Authentication with timeout handling
     */
    private Optional<Boolean> authenticateWithLDAP(String username, String password) {
        DirContext ctx = null;
        try {
            var ldapEnvironment = createLdapEnvironment(username, password);
            ctx = new InitialDirContext(ldapEnvironment);

            boolean isAuthenticated = validateUserInGroup(ctx, username);
            logService.logMessage("ldap", "auth_attempt",
                    "LDAP authentication " + (isAuthenticated ? "successful" : "failed") + " for: " + username,
                    username, isAuthenticated ? "success" : "failure");
            return Optional.of(isAuthenticated);

        } catch (NamingException | RuntimeException e) {
            log.log(Level.WARNING, "LDAP authentication failed for user: " + username, e);
            logService.logMessage("ldap", "auth_error",
                    "LDAP authentication error for: " + username + " - " + e.getMessage(),
                    username, "error");
            return Optional.empty();
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException e) {
                    log.log(Level.WARNING, "Failed to close LDAP context", e);
                }
            }
        }
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

    private Hashtable<String, String> createLdapEnvironment(String username, String password) {
        var env = new Hashtable<String, String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_CONTEXT_FACTORY);
        env.put(Context.PROVIDER_URL, ldapUrl);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, username + DOMAIN_SUFFIX);
        env.put(Context.SECURITY_CREDENTIALS, password);
        env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(ldapTimeout));
        env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(ldapTimeout));
        return env;
    }

    private boolean validateUserInGroup(DirContext ctx, String username) throws NamingException {
        var searchFilter = "(sAMAccountName=" + username + ")";
        var searchControls = createSearchControls();

        NamingEnumeration<SearchResult> searchResults = null;
        try {
            searchResults = ctx.search(ldapBase, searchFilter, searchControls);

            if (!searchResults.hasMore()) {
                log.info("User not found in LDAP: " + username);
                return false;
            }

            var result = searchResults.next();
            return checkGroupMembership(result, username);

        } finally {
            if (searchResults != null) {
                try {
                    searchResults.close();
                } catch (NamingException e) {
                    log.log(Level.WARNING, "Failed to close LDAP search results", e);
                }
            }
        }
    }

    private SearchControls createSearchControls() {
        var controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(MEMBER_OF_ATTRIBUTE);
        controls.setTimeLimit(ldapTimeout);
        return controls;
    }

    private boolean checkGroupMembership(SearchResult result, String username) throws NamingException {
        var attrs = result.getAttributes();
        var memberOf = attrs.get("memberOf");

        if (memberOf == null) {
            log.info("User has no group memberships: " + username);
            return false;
        }

        var groupDNs = Collections.list(memberOf.getAll());

        boolean hasAccess = groupDNs.stream()
                .map(String.class::cast)
                .anyMatch(groupDN -> groupDN.contains(TARGET_GROUP));

        if (hasAccess) {
            log.info("Successfully authenticated LDAP user: " + username);
        } else {
            log.info("User not member of required group: " + username);
        }

        return hasAccess;
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

    public boolean isLdapAvailable() {
        if (!ldapEnabled) return false;

        DirContext ctx = null;
        try {
            var env = new Hashtable<String, String>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_CONTEXT_FACTORY);
            env.put(Context.PROVIDER_URL, ldapUrl);
            env.put("com.sun.jndi.ldap.connect.timeout", "3000");

            ctx = new InitialDirContext(env);
            return true;
        } catch (Exception e) {
            log.log(Level.WARNING, "LDAP health check failed", e);
            return false;
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException e) {
                    log.log(Level.WARNING, "Failed to close LDAP context in health check", e);
                }
            }
        }
    }

    private void initializeDefaultData() {
        fileService.createDefaultFiles();
    }
}