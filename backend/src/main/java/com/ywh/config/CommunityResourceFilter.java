package com.ywh.config;

import com.ywh.entity.UserRoleEntity;
import com.ywh.repository.CommitteeMeetingRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 对所有以会议ID为路径的接口统一做对象级小区隔离。
 * 防止某个业务方法遗漏校验后，用户通过猜测其他小区的会议ID越权访问。
 */
@Component
@RequiredArgsConstructor
public class CommunityResourceFilter extends OncePerRequestFilter {
    private static final Pattern COMMITTEE_PATH = Pattern.compile("^/api/committees/(\\d+)(?:/.*)?$");
    private final CommitteeMeetingRepository meetingRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Matcher matcher = COMMITTEE_PATH.matcher(request.getRequestURI());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (matcher.matches() && authentication != null
                && authentication.getPrincipal() instanceof UserRoleEntity identity
                && !identity.getRole().isTechnicalAdmin()) {
            Long meetingId = Long.valueOf(matcher.group(1));
            boolean allowed = identity.getCommunity() != null
                    && meetingRepository.existsByIdAndCommunityId(
                            meetingId, identity.getCommunity().getId());
            if (!allowed) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":404,\"message\":\"会议不存在\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
