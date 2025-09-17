package com.example.emergencyservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Configuration class for LDAP settings
@Service
public class LdapConfigService {

    @Value("${ldap.url:ldap://your-ldap-server:389}")
    private String ldapUrl;

    @Value("${ldap.base:DC=hq,DC=paynet,DC=uz}")
    private String ldapBase;

    @Value("${ldap.group:CN=office-app}")
    private String targetGroup;

    @Value("${ldap.domain:@hq.paynet.uz}")
    private String domainSuffix;

    // Getters
    public String getLdapUrl() { return ldapUrl; }
    public String getLdapBase() { return ldapBase; }
    public String getTargetGroup() { return targetGroup; }
    public String getDomainSuffix() { return domainSuffix; }
}
