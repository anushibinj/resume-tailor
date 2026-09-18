package com.resumetailor.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the app's own session token (issued by {@link JwtService} after a Google
 * sign-in) from the Authorization header and, if valid, populates the security context
 * for the rest of the request.
 *
 * <p>Leaves the context empty on a missing or invalid token rather than rejecting the
 * request itself -- {@code SecurityConfig}'s {@code anyRequest().authenticated()} is
 * what turns an empty context into a 401 for anything that needs one.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            JwtService.AuthenticatedPrincipal principal = jwtService.parse(header.substring(BEARER_PREFIX.length()));
            if (principal != null) {
                List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal.userId(), null, authorities));
            }
        }
        filterChain.doFilter(request, response);
    }
}
