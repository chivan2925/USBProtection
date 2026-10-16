package com.group.usbshield.server.auth;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {
    private final AdminUserRepository users;
    private final PasswordEncoder encoder;
    @Value("${usbshield.admin.username:admin}") private String username;
    @Value("${usbshield.admin.password:}") private String password;
    public void run(ApplicationArguments args){if(!password.isBlank() && !users.existsByUsername(username)) users.save(AdminUser.builder().username(username).passwordHash(encoder.encode(password)).build());}
}
