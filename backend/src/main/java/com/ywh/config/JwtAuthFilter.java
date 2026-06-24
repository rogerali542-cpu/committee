package com.ywh.config;

import com.ywh.entity.UserRoleEntity;
import com.ywh.repository.UserRoleRepository;
import com.ywh.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRoleRepository userRoleRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null) {
            UserRoleEntity activeRole = null;

            // Dev mode: token is "dev-token-<roleId>"
            if (token.startsWith("dev-token-")) {
                Long roleId = Long.valueOf(token.substring("dev-token-".length()));
                activeRole = userRoleRepository.findById(roleId).orElse(null);
            }
            // Production: JWT token
            else if (jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.getUserId(token);
                String roleIdHeader = request.getHeader("X-Active-Role-Id");
                List<UserRoleEntity> roles = userRoleRepository.findByUserId(userId);
                if (!roles.isEmpty()) {
                    if (roleIdHeader != null) {
                        Long roleId = Long.valueOf(roleIdHeader);
                        activeRole = roles.stream()
                                .filter(r -> r.getId().equals(roleId))
                                .findFirst().orElse(null);
                    }
                    if (activeRole == null) {
                        activeRole = roles.get(0);
                    }
                }
            }

            if (activeRole != null) {
                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + activeRole.getRole().name())
                );
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(activeRole, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
