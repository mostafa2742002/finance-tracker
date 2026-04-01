package com.financetracker.finance_tracker.common.jwt;

import java.io.IOException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SecurityException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.financetracker.finance_tracker.common.ratelimit.RateLimitDecision;
import com.financetracker.finance_tracker.common.ratelimit.RateLimitResponseWriter;
import com.financetracker.finance_tracker.common.ratelimit.RateLimitService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final RateLimitService rateLimitService;
    private final RateLimitResponseWriter rateLimitResponseWriter;

    public JwtAuthFilter(JwtUtils jwtUtils,
            UserDetailsService userDetailsService,
            AuthenticationEntryPoint authenticationEntryPoint,
            RateLimitService rateLimitService,
            RateLimitResponseWriter rateLimitResponseWriter) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.rateLimitService = rateLimitService;
        this.rateLimitResponseWriter = rateLimitResponseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/v1/auth/")
                || path.equals("/api/v1/auth")
                || path.startsWith("/auth/")
                || path.equals("/auth")
                || path.startsWith("/ws/")
                || path.startsWith("/actuator/health")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/actuator/metrics")
                || path.equals("/actuator/metrics/**");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = jwtUtils.extractAllClaims(token);
                String email = claims.getSubject();
                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    RateLimitDecision rateLimitDecision = rateLimitService.checkGeneralRequestLimit(email);
                    if (!rateLimitDecision.allowed()) {
                        rateLimitResponseWriter.writeTooManyRequests(
                                response,
                                "Too many requests. Please try again later.",
                                rateLimitDecision.retryAfterSeconds());
                        return;
                    }
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                    if (jwtUtils.isTokenValid(claims, userDetails)) {
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (ExpiredJwtException
                    | UnsupportedJwtException
                    | MalformedJwtException
                    | SecurityException
                    | IllegalArgumentException
                    | UsernameNotFoundException ex) {
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(
                        request,
                        response,
                        new InsufficientAuthenticationException("Invalid or expired token", ex));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
