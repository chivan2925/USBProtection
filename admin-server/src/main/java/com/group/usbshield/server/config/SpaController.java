package com.group.usbshield.server.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Only known navigation paths fall back to the SPA; never rewrite API/assets. */
@Controller
public class SpaController {
    @GetMapping({"/login", "/dashboard", "/endpoints", "/endpoints/{id}",
            "/devices", "/whitelist", "/blocked", "/events", "/settings"})
    public String page(jakarta.servlet.http.HttpServletRequest request) {
        return "redirect:/#" + request.getRequestURI().substring(request.getContextPath().length());
    }
}
