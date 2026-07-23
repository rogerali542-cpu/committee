package com.ywh.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login").permitAll()
                // 测试期身份名单（登录页选身份用，登录前无 token）。⚠ 上线前关闭（docs/上线前TODO.md）
                .requestMatchers("/api/auth/dev-roles").permitAll()
                .requestMatchers("/api/share/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll()
                .requestMatchers("/api/public-info/**").permitAll()
                // 音频文件需被火山引擎(豆包ASR)匿名拉取，故公开（仅 GET 静态音频）
                .requestMatchers(HttpMethod.GET, "/api/quick-audio/**").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/api/quick-audio/**").permitAll()
                // 企业微信 JS-SDK 签名：内容不含机密（公开 URL + ticket 计算），放开鉴权
                .requestMatchers(HttpMethod.GET, "/api/wecom/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
