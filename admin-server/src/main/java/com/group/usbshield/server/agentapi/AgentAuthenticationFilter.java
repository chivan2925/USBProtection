package com.group.usbshield.server.agentapi;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.usbshield.server.common.ApiExceptionHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
public class AgentAuthenticationFilter extends OncePerRequestFilter {
    private final AgentCredentialService credentials;
    private final ObjectMapper mapper;
    public AgentAuthenticationFilter(AgentCredentialService credentials,ObjectMapper mapper){this.credentials=credentials;this.mapper=mapper;}
    @Override protected boolean shouldNotFilter(HttpServletRequest r){String path=r.getRequestURI().substring(r.getContextPath().length());return !path.startsWith("/api/agent/") || path.equals("/api/agent/enroll");}
    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain) throws ServletException,IOException {
        try {
            var endpoint=credentials.authenticate(r.getHeader("X-Endpoint-Id"),r.getHeader("X-Agent-Token"));
            var context=SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(endpoint.getEndpointId(),null,List.of(new SimpleGrantedAuthority("ROLE_AGENT"))));
            SecurityContextHolder.setContext(context);
        } catch(ResponseStatusException e){s.setStatus(e.getStatusCode().value());s.setContentType("application/json");mapper.writeValue(s.getOutputStream(),ApiExceptionHandler.body(s.getStatus(),e.getReason()));return;}
        chain.doFilter(r,s);
    }
}
