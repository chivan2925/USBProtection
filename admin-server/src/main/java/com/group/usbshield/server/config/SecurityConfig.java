package com.group.usbshield.server.config;
import com.group.usbshield.server.agentapi.*;
import com.group.usbshield.server.auth.*;
import com.group.usbshield.server.common.ApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.*;
@Configuration
@org.springframework.context.annotation.Profile("!admin-reset")
public class SecurityConfig {
    @Bean UserDetailsService users(AdminUserRepository users){return name->users.findByUsername(name)
        .map(u->User.withUsername(u.getUsername()).password(u.getPasswordHash()).roles("ADMIN").build())
        .orElseThrow(()->new UsernameNotFoundException("Invalid credentials"));}
    @Bean SecurityFilterChain security(HttpSecurity http,AgentCredentialService credentials,ObjectMapper mapper,AdminUserRepository adminUsers) throws Exception {
        http.csrf(c->c.ignoringRequestMatchers("/api/agent/**")
            .csrfTokenRepository(new HttpSessionCsrfTokenRepository())
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .addFilterBefore(new AgentAuthenticationFilter(credentials,mapper),UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new AdminSessionVersionFilter(adminUsers), org.springframework.security.web.access.intercept.AuthorizationFilter.class)
            .authorizeHttpRequests(a->a.requestMatchers("/api/agent/enroll","/api/auth/csrf","/api/auth/login").permitAll()
                .requestMatchers("/api/agent/**").hasRole("AGENT")
                .requestMatchers("/api/admin/**","/api/auth/me","/api/auth/logout").hasRole("ADMIN")
                .requestMatchers("/api/**").denyAll().anyRequest().permitAll())
            .exceptionHandling(e->e.authenticationEntryPoint((r,s,x)->{s.setStatus(401);s.setContentType("application/json");mapper.writeValue(s.getOutputStream(),ApiExceptionHandler.body(401,"Authentication required"));})
                .accessDeniedHandler((r,s,x)->{s.setStatus(403);s.setContentType("application/json");mapper.writeValue(s.getOutputStream(),ApiExceptionHandler.body(403,"Access denied or CSRF token missing"));}))
            .requestCache(c->c.disable());
        return http.build();
    }
}
