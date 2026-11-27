package com.group.usbshield.server.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminSessionVersionFilterTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void changedPasswordRevokesOldAuthenticatedSessionBeforeAdminApi() throws Exception {
        var repo = mock(AdminUserRepository.class);
        var workspaces = mock(WorkspaceRepository.class);
        when(workspaces.existsByIdAndOwnerAdminId(2L, 1L)).thenReturn(true);
        var user = AdminUser.builder().username("admin").credentialVersion(9L).build();
        when(repo.findById(1L)).thenReturn(Optional.of(user));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new AdminIdentity(1L, 2L, "admin"), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        SecurityContextHolder.setContext(context);
        var request = new MockHttpServletRequest("GET", "/api/admin/endpoints");
        request.setServletPath("/api/admin/endpoints");
        var session = request.getSession();
        session.setAttribute(AdminSessionVersionFilter.SESSION_VERSION, 8L);
        var response = new MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);
        new AdminSessionVersionFilter(repo, workspaces).doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test void currentSessionRetainsAccess() throws Exception {
        var repo = mock(AdminUserRepository.class);
        var workspaces = mock(WorkspaceRepository.class);
        when(workspaces.existsByIdAndOwnerAdminId(2L, 1L)).thenReturn(true);
        when(repo.findById(1L)).thenReturn(Optional.of(
                AdminUser.builder().username("admin").credentialVersion(9L).build()));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new AdminIdentity(1L, 2L, "admin"), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        SecurityContextHolder.setContext(context);
        var request = new MockHttpServletRequest("GET", "/api/admin/endpoints");
        request.setServletPath("/api/admin/endpoints");
        request.getSession().setAttribute(AdminSessionVersionFilter.SESSION_VERSION, 9L);
        var response = new MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);
        new AdminSessionVersionFilter(repo, workspaces).doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
}
