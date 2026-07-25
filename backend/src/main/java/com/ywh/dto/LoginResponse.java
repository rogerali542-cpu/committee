package com.ywh.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class LoginResponse {
    private String token;
    private Long userId;
    private List<RoleDTO> roles;
    private RoleDTO activeRole;

    @Data
    @Builder
    public static class RoleDTO {
        private Long id;
        private String role;
        private String realName;
        private Long communityId;
        private String communityName;
        private Boolean enabled;
        private String scopeLevel;
        private String scopeRegionCode;
        private String scopeRegionName;
    }
}
