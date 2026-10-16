package com.group.usbshield.server.auth;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AdminAuthController {
    private final UserDetailsService users;
    private final PasswordEncoder encoder;
    public record Login(@NotBlank String username,@NotBlank String password){}
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
    @PostMapping("/login") public Map<String,String> login(@Valid @RequestBody Login body,HttpServletRequest request,HttpServletResponse response){
        org.springframework.security.core.userdetails.UserDetails user;
        try{user=users.loadUserByUsername(body.username());}catch(AuthenticationException e){throw new ResponseStatusException(UNAUTHORIZED,"Invalid credentials");}
        if(!encoder.matches(body.password(),user.getPassword())) throw new ResponseStatusException(UNAUTHORIZED,"Invalid credentials");
        if(request.getSession(false)!=null)request.changeSessionId();
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user.getUsername(),null,user.getAuthorities()));
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context,request,response);
        new HttpSessionCsrfTokenRepository().saveToken(null,request,response);
        return Map.of("username",user.getUsername());
    }
    @GetMapping("/me") public Map<String,String> me(Authentication auth){return Map.of("username",auth.getName());}
    @PostMapping("/logout") public Map<String,Boolean> logout(HttpServletRequest request){
        if(request.getSession(false)!=null)request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();return Map.of("success",true);
    }
}
