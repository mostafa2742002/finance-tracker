package com.financetracker.finance_tracker.common.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import com.financetracker.finance_tracker.common.jwt.JwtUtils;
import com.financetracker.finance_tracker.user.entity.User;
import com.financetracker.finance_tracker.user.repository.UserRepo;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;
    private final UserRepo userRepo;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Missing Authorization header");
        }

        String token = authHeader.substring(7);
        Claims claims = jwtUtils.extractAllClaims(token);
        String email = claims.getSubject();

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Invalid token subject");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        if (!jwtUtils.isTokenValid(claims, userDetails)) {
            throw new IllegalArgumentException("Invalid or expired token");
        }

        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user.getId().toString(),
                null,
                userDetails.getAuthorities());

        accessor.setUser(authentication);
        return message;
    }
}
