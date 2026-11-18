package com.group.usbshield.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SpaControllerTest {
    @Test
    void demoKeepsItsPathAndAdminRoutesKeepTheirRedirects() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new SpaController()).build();
        for (String path : new String[]{"/demo", "/demo/"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
        mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/#/dashboard"));
        mvc.perform(get("/api/missing")).andExpect(status().isNotFound());
    }
}
